plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.remotebot.core.push"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":core:common"))
    implementation(project(":core:network"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Firebase FCM
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    // Huawei HMS Push & AGC
    implementation(libs.huawei.hms.push)
    implementation(libs.huawei.agconnect.core)

    implementation(libs.koin.core)
    implementation(libs.koin.android)

    testImplementation(kotlin("test"))
}
