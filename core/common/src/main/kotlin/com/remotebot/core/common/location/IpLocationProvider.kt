package com.remotebot.core.common.location

import android.location.Location
import android.os.Bundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Резервный сетевой IP-геолокатор для определения приблизительных координат устройства,
 * когда недоступны спутники GPS, сотовые вышки и Fused Location Provider.
 */
object IpLocationProvider {
    private val ENDPOINTS =
        listOf(
            "https://ipinfo.io/json",
            "https://ipapi.co/json/",
        )

    suspend fun fetchLocation(): Location? =
        withContext(Dispatchers.IO) {
            for (urlString in ENDPOINTS) {
                try {
                    val url = URL(urlString)
                    val conn =
                        (url.openConnection() as HttpURLConnection).apply {
                            connectTimeout = 4000
                            readTimeout = 4000
                            setRequestProperty("User-Agent", "RemoteBot/2.0 (Android)")
                        }
                    if (conn.responseCode == 200) {
                        val response = conn.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(response)
                        var lat: Double? = null
                        var lon: Double? = null
                        if (json.has("loc")) {
                            val parts = json.getString("loc").split(",")
                            if (parts.size == 2) {
                                lat = parts[0].trim().toDoubleOrNull()
                                lon = parts[1].trim().toDoubleOrNull()
                            }
                        }
                        if (lat == null && (json.has("lat") || json.has("latitude"))) {
                            lat = if (json.has("lat")) json.getDouble("lat") else json.getDouble("latitude")
                            lon = if (json.has("lon")) json.getDouble("lon") else json.getDouble("longitude")
                        }
                        if (lat != null && lon != null) {
                            val loc =
                                Location("ip").apply {
                                    latitude = lat
                                    longitude = lon
                                    accuracy = 1500f
                                    time = System.currentTimeMillis()
                                }
                            val bundle = Bundle()
                            val city = json.optString("city")
                            val region = json.optString("region").ifEmpty { json.optString("regionName") }
                            val country = json.optString("country").ifEmpty { json.optString("country_name") }
                            val queryIp = json.optString("ip").ifEmpty { json.optString("query") }
                            val isp = json.optString("org").ifEmpty { json.optString("isp") }
                            if (city.isNotBlank()) bundle.putString("city", city)
                            if (region.isNotBlank()) bundle.putString("region", region)
                            if (country.isNotBlank()) bundle.putString("country", country)
                            if (queryIp.isNotBlank()) bundle.putString("ip", queryIp)
                            if (isp.isNotBlank()) bundle.putString("isp", isp)
                            loc.extras = bundle
                            return@withContext loc
                        }
                    }
                } catch (_: Exception) {
                }
            }
            null
        }
}
