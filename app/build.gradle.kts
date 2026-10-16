import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import java.util.TimeZone

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}
if (file("agconnect-services.json").exists()) {
    apply(plugin = "com.huawei.agconnect")
}

android {
    namespace = "com.remotebot.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.remotebot.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        val buildTime =
            SimpleDateFormat("dd.MM.yyyy HH:mm:ss 'UTC'", Locale.US)
                .apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date())
        val gitCommit =
            providers
                .exec {
                    commandLine("git", "rev-parse", "--short", "HEAD")
                    isIgnoreExitValue = true
                }.standardOutput.asText
                .get()
                .trim()
                .ifEmpty { "unknown" }
        buildConfigField("String", "BUILD_TIME", "\"$buildTime\"")
        buildConfigField("String", "GIT_COMMIT", "\"$gitCommit\"")
        buildConfigField("String", "BUILD_ID", "\"$gitCommit\"")
        buildConfigField("String", "GITHUB_URL", "\"https://github.com/runalsh/remote-bot-android\"")
        buildConfigField("String", "TELEGRAM_AUTHOR_URL", "\"https://t.me/runalsh\"")
        buildConfigField("String", "TELEGRAM_AUTHOR_USERNAME", "\"@runalsh\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystorePropsFile = rootProject.file("keystore.properties")
            val props = Properties()
            if (keystorePropsFile.exists()) {
                keystorePropsFile.inputStream().use { props.load(it) }
            }

            val storeFilePath = props.getProperty("storeFile") ?: System.getenv("KEYSTORE_FILE")
            val storePasswordVal = props.getProperty("storePassword") ?: System.getenv("KEYSTORE_PASSWORD")
            val keyAliasVal = props.getProperty("keyAlias") ?: System.getenv("KEY_ALIAS")
            val keyPasswordVal = props.getProperty("keyPassword") ?: System.getenv("KEY_PASSWORD")

            val resolvedStoreFile = if (!storeFilePath.isNullOrBlank()) {
                val f = file(storeFilePath)
                if (f.exists()) f else rootProject.file(storeFilePath)
            } else null

            if (resolvedStoreFile != null && resolvedStoreFile.exists() && !keyAliasVal.isNullOrBlank()) {
                storeFile = resolvedStoreFile
                storePassword = storePasswordVal
                keyAlias = keyAliasVal
                keyPassword = keyPasswordVal ?: storePasswordVal
            } else {
                // Фоллбэк на debug.keystore, если production ключ не настроен
                initWith(getByName("debug"))
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(project(":core:proxy"))
    implementation(project(":core:push"))
    implementation(project(":core:database"))
    implementation(project(":core:security"))
    implementation(project(":channels:telegram-bot"))

    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Firebase FCM
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    implementation(libs.libsu.core)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
