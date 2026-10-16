buildscript {
    repositories {
        google()
        mavenCentral()
        maven { url = java.net.URI("https://developer.huawei.com/repo/") }
    }
    dependencies {
        classpath("com.huawei.agconnect:agcp:1.9.6.300")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "io.gitlab.arturbosch.detekt")

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        android.set(true)
        ignoreFailures.set(true)
        reporters {
            reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
        }
    }

    configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        ignoreFailures = true
        parallel = true
        config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    }

    plugins.withId("com.android.application") {
        configure<com.android.build.api.dsl.ApplicationExtension> {
            lint {
                abortOnError = false
                checkDependencies = true
            }
        }
    }

    plugins.withId("com.android.library") {
        configure<com.android.build.api.dsl.LibraryExtension> {
            lint {
                abortOnError = false
            }
        }
    }

    plugins.withId("com.android.lint") {
        configure<com.android.build.api.dsl.Lint> {
            abortOnError = false
        }
    }
}

