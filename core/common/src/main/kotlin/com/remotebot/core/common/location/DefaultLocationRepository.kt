package com.remotebot.core.common.location

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.SensorManager
import android.location.Criteria
import android.location.Geocoder
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.google.android.gms.tasks.CancellationTokenSource
import com.remotebot.core.common.logger.AppLogger
import com.remotebot.domain.model.LiveTrackingStatus
import com.remotebot.domain.model.LocationData
import com.remotebot.domain.model.ShakeDetectorConfig
import com.remotebot.domain.provider.RootStatusProvider
import com.remotebot.domain.repository.LocationRepository
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume
import com.google.android.gms.location.LocationServices as GoogleLocationServices
import com.google.android.gms.location.Priority as GooglePriority
import com.huawei.hms.location.LocationCallback as HuaweiLocationCallback
import com.huawei.hms.location.LocationRequest as HuaweiLocationRequest
import com.huawei.hms.location.LocationResult as HuaweiLocationResult
import com.huawei.hms.location.LocationServices as HuaweiLocationServices

class DefaultLocationRepository(
    private val context: Context,
    private val rootStatusProvider: RootStatusProvider,
) : LocationRepository {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val googleFusedClient by lazy {
        try {
            GoogleLocationServices.getFusedLocationProviderClient(context)
        } catch (_: Throwable) {
            null
        }
    }

    private val huaweiFusedClient by lazy {
        try {
            HuaweiLocationServices.getFusedLocationProviderClient(context)
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun <T> com.huawei.hmf.tasks.Task<T>.awaitHuawei(): T? =
        suspendCancellableCoroutine { cont ->
            addOnSuccessListener { result ->
                if (cont.isActive) cont.resume(result)
            }
            addOnFailureListener {
                if (cont.isActive) cont.resume(null)
            }
            addOnCanceledListener {
                if (cont.isActive) cont.resume(null)
            }
        }

    private val shakeDetectorManager = ShakeDetectorManager(context)
    override val shakeDetectorConfig: StateFlow<ShakeDetectorConfig> = shakeDetectorManager.shakeDetectorConfig
    override val shakeEvents: Flow<Unit> = shakeDetectorManager.shakeEvents

    // Live / периодическое отслеживание местоположения
    private val _liveTrackingStatus = MutableStateFlow(LiveTrackingStatus())
    override val liveTrackingStatus: StateFlow<LiveTrackingStatus> = _liveTrackingStatus.asStateFlow()

    private val _liveLocationUpdates = MutableSharedFlow<LocationData>(extraBufferCapacity = 16)
    override val liveLocationUpdates: Flow<LocationData> = _liveLocationUpdates.asSharedFlow()

    private var liveTrackingJob: Job? = null
    private val trackingScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Снимок данных GNSS спутников
    private data class GnssStatusSnapshot(
        val visibleSatellites: Int = 0,
        val usedInFix: Int = 0,
        val avgCn0: Float = 0f,
        val maxCn0: Float = 0f,
        val constellations: Set<String> = emptySet(),
        val timestampMs: Long = 0L,
    )

    @Volatile
    private var latestGnssSnapshot: GnssStatusSnapshot? = null

    @Volatile
    private var cachedLastLocation: Location? = null

    @SuppressLint("MissingPermission")
    override suspend fun getCurrentLocation(timeoutSeconds: Int): Result<LocationData> =
        withContext(Dispatchers.IO) {
            runCatching {
                val now = System.currentTimeMillis()
                var bestRecentLocation: Location? = null

                // 1. Проверяем кэш Google Fused Location Provider
                try {
                    val googleLast = googleFusedClient?.lastLocation?.await()
                    if (googleLast != null) {
                        val b = googleLast.extras ?: Bundle()
                        b.putString("fused_engine", "google")
                        googleLast.extras = b
                        bestRecentLocation = googleLast
                    }
                } catch (_: Exception) {
                }

                // 2. Проверяем кэш Huawei HMS Fused Location Provider
                if (bestRecentLocation == null) {
                    try {
                        val huaweiLast = huaweiFusedClient?.lastLocation?.awaitHuawei()
                        if (huaweiLast != null) {
                            val b = huaweiLast.extras ?: Bundle()
                            b.putString("fused_engine", "huawei")
                            huaweiLast.extras = b
                            bestRecentLocation = huaweiLast
                        }
                    } catch (_: Exception) {
                    }
                }

                // 3. Проверяем кэш всех системных LocationManager провайдеров
                val allProviders =
                    try {
                        locationManager.allProviders
                    } catch (_: Exception) {
                        listOf(
                            LocationManager.FUSED_PROVIDER,
                            LocationManager.GPS_PROVIDER,
                            LocationManager.NETWORK_PROVIDER,
                            LocationManager.PASSIVE_PROVIDER,
                        )
                    }
                for (provider in allProviders) {
                    try {
                        val loc = locationManager.getLastKnownLocation(provider)
                        if (loc != null) {
                            if (bestRecentLocation == null || loc.time > bestRecentLocation.time) {
                                bestRecentLocation = loc
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
                if (bestRecentLocation == null) {
                    bestRecentLocation = cachedLastLocation
                }

                // Если есть свежий спутниковый фикс (< 60 сек), возвращаем его сразу
                if (bestRecentLocation != null &&
                    bestRecentLocation.provider == LocationManager.GPS_PROVIDER &&
                    (now - bestRecentLocation.time) < 60_000
                ) {
                    return@runCatching buildLocationData(bestRecentLocation, latestGnssSnapshot)
                }

                // 4. Пытаемся получить свежий фикс через Google Fused Location Provider Client (getCurrentLocation)
                var fusedFreshLoc: Location? = null
                try {
                    val cts = CancellationTokenSource()
                    fusedFreshLoc =
                        withTimeoutOrNull((timeoutSeconds * 1000L).coerceAtLeast(3000L)) {
                            googleFusedClient
                                ?.getCurrentLocation(
                                    GooglePriority.PRIORITY_HIGH_ACCURACY,
                                    cts.token,
                                )?.await()
                        }
                    if (fusedFreshLoc != null) {
                        val b = fusedFreshLoc.extras ?: Bundle()
                        b.putString("fused_engine", "google")
                        fusedFreshLoc.extras = b
                        cachedLastLocation = fusedFreshLoc
                        // Если точность отличная (<= 50м) или это прямой GPS, сразу возвращаем
                        if (fusedFreshLoc.accuracy <= 50f || fusedFreshLoc.provider == LocationManager.GPS_PROVIDER) {
                            return@runCatching buildLocationData(fusedFreshLoc, latestGnssSnapshot)
                        }
                    }
                } catch (_: Exception) {
                }

                // 5. Если Google Fused не дал результат, пробуем Huawei HMS Fused Location Provider Client
                if (fusedFreshLoc == null && huaweiFusedClient != null) {
                    try {
                        fusedFreshLoc =
                            withTimeoutOrNull((timeoutSeconds * 1000L).coerceAtLeast(3000L)) {
                                suspendCancellableCoroutine<Location?> { cont ->
                                    val callback =
                                        object : HuaweiLocationCallback() {
                                            override fun onLocationResult(result: HuaweiLocationResult?) {
                                                val loc = result?.lastLocation
                                                if (loc != null && cont.isActive) {
                                                    try {
                                                        huaweiFusedClient?.removeLocationUpdates(this)
                                                    } catch (_: Exception) {
                                                    }
                                                    val b = loc.extras ?: Bundle()
                                                    b.putString("fused_engine", "huawei")
                                                    loc.extras = b
                                                    cont.resume(loc)
                                                }
                                            }
                                        }
                                    val request =
                                        HuaweiLocationRequest.create().apply {
                                            priority = HuaweiLocationRequest.PRIORITY_HIGH_ACCURACY
                                            numUpdates = 1
                                            interval = 1000L
                                            fastestInterval = 500L
                                        }
                                    try {
                                        huaweiFusedClient?.requestLocationUpdates(request, callback, Looper.getMainLooper())
                                        cont.invokeOnCancellation {
                                            try {
                                                huaweiFusedClient?.removeLocationUpdates(callback)
                                            } catch (_: Exception) {
                                            }
                                        }
                                    } catch (_: Exception) {
                                        if (cont.isActive) cont.resume(null)
                                    }
                                }
                            }
                        if (fusedFreshLoc != null) {
                            cachedLastLocation = fusedFreshLoc
                            if (fusedFreshLoc.accuracy <= 50f || fusedFreshLoc.provider == LocationManager.GPS_PROVIDER) {
                                return@runCatching buildLocationData(fusedFreshLoc, latestGnssSnapshot)
                            }
                        }
                    } catch (_: Exception) {
                    }
                }

                val hasGps =
                    try {
                        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    } catch (_: Exception) {
                        false
                    }
                val hasNetwork =
                    try {
                        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                    } catch (_: Exception) {
                        false
                    }

                val searchStartTime = System.currentTimeMillis()
                var candidateLoc: Location? = fusedFreshLoc

                // Запрашиваем свежие координаты с тайм-аутом
                val freshLoc =
                    withTimeoutOrNull(timeoutSeconds * 1000L) {
                        suspendCancellableCoroutine<Location?> { continuation ->
                            val handler = Handler(Looper.getMainLooper())

                            val gnssCallback =
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && hasGps) {
                                    object : GnssStatus.Callback() {
                                        override fun onSatelliteStatusChanged(status: GnssStatus) {
                                            val count = status.satelliteCount
                                            var used = 0
                                            var sumUsedCn0 = 0f
                                            var sumTotalCn0 = 0f
                                            var maxCn0 = 0f
                                            val consts = mutableSetOf<String>()

                                            for (i in 0 until count) {
                                                val cn0 = status.getCn0DbHz(i)
                                                if (cn0 > maxCn0) maxCn0 = cn0
                                                sumTotalCn0 += cn0

                                                val cType =
                                                    when (status.getConstellationType(i)) {
                                                        GnssStatus.CONSTELLATION_GPS -> "GPS"
                                                        GnssStatus.CONSTELLATION_GLONASS -> "ГЛОНАСС"
                                                        GnssStatus.CONSTELLATION_GALILEO -> "Galileo"
                                                        GnssStatus.CONSTELLATION_BEIDOU -> "BeiDou"
                                                        else -> "GNSS"
                                                    }
                                                consts.add(cType)

                                                if (status.usedInFix(i)) {
                                                    used++
                                                    sumUsedCn0 += cn0
                                                }
                                            }

                                            val avg =
                                                if (used > 0) {
                                                    sumUsedCn0 / used
                                                } else if (count > 0) {
                                                    sumTotalCn0 / count
                                                } else {
                                                    0f
                                                }
                                            latestGnssSnapshot =
                                                GnssStatusSnapshot(
                                                    visibleSatellites = count,
                                                    usedInFix = used,
                                                    avgCn0 = avg,
                                                    maxCn0 = maxCn0,
                                                    constellations = consts,
                                                    timestampMs = System.currentTimeMillis(),
                                                )
                                        }
                                    }
                                } else {
                                    null
                                }

                            lateinit var cleanupFn: () -> Unit

                            val generalListener =
                                object : LocationListener {
                                    override fun onLocationChanged(loc: Location) {
                                        val isGps = loc.provider == LocationManager.GPS_PROVIDER
                                        candidateLoc = loc
                                        cachedLastLocation = loc
                                        if (isGps && continuation.isActive) {
                                            cleanupFn()
                                            continuation.resume(loc)
                                        } else {
                                            // Если пришла точка от сети/fused/criteria:
                                            val elapsed = System.currentTimeMillis() - searchStartTime
                                            if ((elapsed >= 2500L || loc.accuracy <= 50f || !hasGps) && continuation.isActive) {
                                                cleanupFn()
                                                continuation.resume(loc)
                                            }
                                        }
                                    }

                                    @Deprecated("Deprecated in Java")
                                    override fun onStatusChanged(
                                        provider: String?,
                                        status: Int,
                                        extras: Bundle?,
                                    ) {}

                                    override fun onProviderEnabled(provider: String) {}

                                    override fun onProviderDisabled(provider: String) {}
                                }

                            cleanupFn = {
                                try {
                                    locationManager.removeUpdates(generalListener)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && gnssCallback != null) {
                                        locationManager.unregisterGnssStatusCallback(gnssCallback)
                                    }
                                } catch (_: Exception) {
                                }
                            }

                            continuation.invokeOnCancellation {
                                cleanupFn()
                            }

                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && gnssCallback != null) {
                                    locationManager.registerGnssStatusCallback(gnssCallback, handler)
                                }

                                // 1. Метод оригинального RemoteBot: Criteria requestSingleUpdate (legacy fallback для старых Android)
                                @Suppress("DEPRECATION")
                                val criteria =
                                    Criteria().apply {
                                        accuracy = Criteria.ACCURACY_COARSE
                                        isAltitudeRequired = false
                                        isBearingRequired = false
                                        isSpeedRequired = false
                                        isCostAllowed = true
                                        horizontalAccuracy = Criteria.ACCURACY_HIGH
                                        verticalAccuracy = Criteria.ACCURACY_HIGH
                                    }
                                try {
                                    @Suppress("DEPRECATION")
                                    locationManager.requestSingleUpdate(criteria, generalListener, Looper.getMainLooper())
                                } catch (_: Exception) {
                                }

                                // 2. Запрос от всех доступных провайдеров
                                if (hasGps) {
                                    try {
                                        locationManager.requestLocationUpdates(
                                            LocationManager.GPS_PROVIDER,
                                            1000L,
                                            0f,
                                            generalListener,
                                            Looper.getMainLooper(),
                                        )
                                    } catch (_: Exception) {
                                    }
                                }
                                if (hasNetwork) {
                                    try {
                                        locationManager.requestLocationUpdates(
                                            LocationManager.NETWORK_PROVIDER,
                                            1000L,
                                            0f,
                                            generalListener,
                                            Looper.getMainLooper(),
                                        )
                                    } catch (_: Exception) {
                                    }
                                }
                                try {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        locationManager.requestLocationUpdates(
                                            LocationManager.FUSED_PROVIDER,
                                            1000L,
                                            0f,
                                            generalListener,
                                            Looper.getMainLooper(),
                                        )
                                    }
                                } catch (_: Exception) {
                                }
                                try {
                                    locationManager.requestLocationUpdates(
                                        LocationManager.PASSIVE_PROVIDER,
                                        1000L,
                                        0f,
                                        generalListener,
                                        Looper.getMainLooper(),
                                    )
                                } catch (_: Exception) {
                                }

                                // 3. Android 11+ getCurrentLocation
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    val providersToQuery = mutableListOf<String>()
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        providersToQuery.add(LocationManager.FUSED_PROVIDER)
                                    }
                                    if (hasNetwork) providersToQuery.add(LocationManager.NETWORK_PROVIDER)
                                    if (hasGps) providersToQuery.add(LocationManager.GPS_PROVIDER)

                                    for (p in providersToQuery) {
                                        try {
                                            locationManager.getCurrentLocation(
                                                p,
                                                null,
                                                context.mainExecutor,
                                            ) { singleLoc ->
                                                if (singleLoc != null) {
                                                    candidateLoc = singleLoc
                                                    cachedLastLocation = singleLoc
                                                    if (singleLoc.provider == LocationManager.GPS_PROVIDER && continuation.isActive) {
                                                        cleanupFn()
                                                        continuation.resume(singleLoc)
                                                    }
                                                }
                                            }
                                        } catch (_: Exception) {
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                if (continuation.isActive) {
                                    cleanupFn()
                                    continuation.resume(candidateLoc)
                                }
                            }
                        }
                    }

                // Окончательный выбор лучшей точки:
                // 1. Свежий фикс (freshLoc)
                // 2. Кандидат из сети/fused/criteria (candidateLoc)
                // 3. Последняя известная точка (bestRecentLocation)
                // 4. Резервный сетевой IP-геолокатор
                val resultLoc =
                    freshLoc ?: candidateLoc ?: bestRecentLocation ?: IpLocationProvider.fetchLocation()?.also {
                        AppLogger.i(
                            "DefaultLocationRepository",
                            "🌐 Резервная геолокация по IP (${it.extras?.getString("city")}, ${it.latitude}, ${it.longitude})",
                        )
                    } ?: throw IllegalStateException(
                        "Не удалось определить координаты: нет сигнала спутников GPS и недоступна сеть/Wi-Fi.",
                    )
                cachedLastLocation = resultLoc
                buildLocationData(resultLoc, latestGnssSnapshot)
            }
        }

    private fun buildLocationData(
        location: Location,
        gnss: GnssStatusSnapshot?,
    ): LocationData {
        val rawProvider = (location.provider ?: "gps").lowercase()
        val isGps = rawProvider == LocationManager.GPS_PROVIDER.lowercase()

        val address =
            try {
                if (Geocoder.isPresent()) {
                    val geocoder = Geocoder(context, Locale.getDefault())

                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    addresses?.firstOrNull()?.getAddressLine(0)
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            } ?: run {
                if (rawProvider == "ip" && location.extras != null) {
                    listOfNotNull(
                        location.extras?.getString("city"),
                        location.extras?.getString("region"),
                        location.extras?.getString("country"),
                    ).filter { it.isNotBlank() }.joinToString(", ").takeIf { it.isNotBlank() }
                } else {
                    null
                }
            }

        var determinationMethod: String
        val signalStrengthLevel: String?
        val satellitesUsed: Int?
        val satellitesVisible: Int?
        val cn0: Float?

        if (isGps) {
            val constStr =
                if (!gnss?.constellations.isNullOrEmpty()) {
                    gnss!!.constellations.joinToString("/")
                } else {
                    "GPS/ГЛОНАСС"
                }
            determinationMethod = "🛰️ Спутниковая навигация ($constStr)"
            satellitesUsed = gnss?.usedInFix ?: location.extras?.getInt("satellites")?.takeIf { it > 0 }
            satellitesVisible = gnss?.visibleSatellites?.takeIf { it > 0 } ?: satellitesUsed
            cn0 = gnss?.avgCn0?.takeIf { it > 0f }

            signalStrengthLevel =
                when {
                    cn0 != null && cn0 >= 35f -> "Отличный (уверенный прием)"
                    cn0 != null && cn0 >= 28f -> "Хороший"
                    cn0 != null && cn0 >= 20f -> "Удовлетворительный (застройка / помехи)"
                    cn0 != null && cn0 > 0f -> "Слабый (приём на границе срыва)"
                    satellitesUsed != null && satellitesUsed > 0 -> "Спутниковый фикс получен"
                    else -> "Прямой сигнал спутников зафиксирован"
                }
        } else if (location.extras?.getString("fused_engine") == "huawei") {
            determinationMethod = "🤖 Huawei HMS Fused Location (Синтез GPS, Wi-Fi и вышек HMS)"
            satellitesUsed = gnss?.usedInFix
            satellitesVisible = gnss?.visibleSatellites
            cn0 = gnss?.maxCn0
            signalStrengthLevel = "Высокая точность (Huawei HMS Location Kit)"
        } else if (rawProvider == "fused" || location.extras?.getString("fused_engine") == "google") {
            determinationMethod = "🤖 Google Fused Location (Синтез GPS, Wi-Fi и сотовых вышек)"
            satellitesUsed = gnss?.usedInFix
            satellitesVisible = gnss?.visibleSatellites
            cn0 = gnss?.maxCn0
            signalStrengthLevel = "Высокая точность (Google Fused Provider)"
        } else if (rawProvider == LocationManager.NETWORK_PROVIDER.lowercase()) {
            determinationMethod = "📡 Косвенные признаки (Вышки связи Cell-ID, точки Wi-Fi)"
            satellitesUsed = 0
            satellitesVisible = gnss?.visibleSatellites
            cn0 = gnss?.maxCn0

            val gpsAvailable = isGpsEnabled()
            signalStrengthLevel =
                when {
                    !gpsAvailable -> "GPS модуль выключен"
                    gnss != null && gnss.visibleSatellites > 0 ->
                        "Слабый (спутников: ${gnss.visibleSatellites}, C/N0: ${"%.1f".format(gnss.maxCn0)} dB-Hz, фикс не пойман)"
                    else -> "Спутниковый сигнал отсутствует (внутри здания / под землей)"
                }
        } else if (rawProvider == "ip") {
            val ipStr = location.extras?.getString("ip")
            val ispStr = location.extras?.getString("isp")
            val details =
                listOfNotNull(
                    if (!ipStr.isNullOrBlank()) "IP: $ipStr" else null,
                    if (!ispStr.isNullOrBlank()) ispStr else null,
                ).joinToString(", ")
            determinationMethod = "🌐 Сетевая геолокация (IP" + (if (details.isNotBlank()) ": $details" else "") + ")"
            satellitesUsed = null
            satellitesVisible = null
            cn0 = null

            val gpsAvailable = isGpsEnabled()
            signalStrengthLevel =
                if (gpsAvailable) {
                    "GPS включен, но спутники недоступны (в помещении). Использован внешний IP."
                } else {
                    "GPS модуль выключен. Координаты определены по сетевому IP-адресу."
                }
        } else {
            determinationMethod = "🔄 Косвенный источник (${rawProvider.uppercase()})"
            satellitesUsed = null
            satellitesVisible = null
            cn0 = null
            signalStrengthLevel = "Координаты получены из системного кэша"
        }

        if (System.currentTimeMillis() - location.time > 60_000L) {
            determinationMethod += " [Кэш]"
        }

        return LocationData(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = location.accuracy,
            altitudeMeters = location.altitude,
            speedMps = location.speed,
            address = address,
            provider = rawProvider,
            timestampMs = location.time,
            isDirectGps = isGps,
            determinationMethod = determinationMethod,
            satellitesUsed = satellitesUsed,
            satellitesVisible = satellitesVisible,
            signalStrengthLevel = signalStrengthLevel,
            cn0DbHz = cn0,
        )
    }

    override fun startLiveTracking(intervalSeconds: Long): Result<Unit> {
        val interval = intervalSeconds.coerceIn(10L, 86400L)
        stopLiveTracking()
        AppLogger.i("DefaultLocationRepository", "📍 Запущен Live Tracking геопозиции (интервал: ${interval}с)...")
        _liveTrackingStatus.value =
            LiveTrackingStatus(
                isActive = true,
                intervalSeconds = interval,
                startedAtMs = System.currentTimeMillis(),
                pointsSent = 0,
                lastLocation = null,
            )

        liveTrackingJob =
            trackingScope.launch {
                var sequence = 0L
                while (isActive && _liveTrackingStatus.value.isActive) {
                    sequence++
                    val result = getCurrentLocation(timeoutSeconds = 20)
                    val loc = result.getOrNull()
                    if (loc != null) {
                        val point = loc.copy(trackingSequenceNumber = sequence)
                        _liveTrackingStatus.update { st ->
                            st.copy(
                                pointsSent = st.pointsSent + 1,
                                lastLocation = point,
                            )
                        }
                        AppLogger.d(
                            "DefaultLocationRepository",
                            "Live Tracking точка #$sequence: lat=${point.latitude}, lng=${point.longitude}, точность=${point.accuracyMeters}м",
                        )
                        _liveLocationUpdates.emit(point)
                    }
                    delay(interval * 1000L)
                }
            }
        return Result.success(Unit)
    }

    override fun stopLiveTracking(): Result<Unit> {
        if (_liveTrackingStatus.value.isActive) {
            AppLogger.i(
                "DefaultLocationRepository",
                "⏹️ Live Tracking геопозиции остановлен (отправлено точек: ${_liveTrackingStatus.value.pointsSent})",
            )
        }
        liveTrackingJob?.cancel()
        liveTrackingJob = null
        _liveTrackingStatus.update { it.copy(isActive = false) }
        return Result.success(Unit)
    }

    override fun isLiveTrackingActive(): Boolean = _liveTrackingStatus.value.isActive

    override suspend fun setGpsEnabled(enabled: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                AppLogger.w("DefaultLocationRepository", "🛰️ Запрос переключения GPS (enabled=$enabled)...")
                if (!rootStatusProvider.isRootGrantedSync()) {
                    throw IllegalStateException("Для переключения GPS требуются Root-права.")
                }
                val cmd =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        "cmd location set-location-enabled $enabled"
                    } else {
                        val mode = if (enabled) 3 else 0
                        "settings put secure location_mode $mode"
                    }
                val result = Shell.cmd(cmd).exec()
                if (!result.isSuccess) {
                    AppLogger.e("DefaultLocationRepository", "❌ Не удалось переключить GPS через Root: ${result.err.joinToString("\n")}")
                    throw IllegalStateException("Shell error: ${result.err.joinToString("\n")}")
                }
                AppLogger.i("DefaultLocationRepository", "✅ Статус GPS успешно изменен (enabled=$enabled)")
            }
        }

    override fun isGpsEnabled(): Boolean =
        try {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        } catch (_: Exception) {
            false
        }

    override fun setShakeDetectorEnabled(
        enabled: Boolean,
        sensitivity: Float,
        cooldownSeconds: Int,
    ) {
        shakeDetectorManager.setShakeDetectorEnabled(enabled, sensitivity, cooldownSeconds)
    }

    override fun simulateShakeEvent() {
        shakeDetectorManager.emitSimulatedShake()
    }
}
