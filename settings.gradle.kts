pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        maven { url = java.net.URI("https://developer.huawei.com/repo/") }
    }
}

    dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = java.net.URI("https://developer.huawei.com/repo/") }
        maven { url = java.net.URI("https://jitpack.io") }
    }
}

rootProject.name = "RemoteBot"

include(":app")
include(":domain")
include(":core:common")
include(":core:network")
include(":core:proxy")
include(":core:push")
include(":core:database")
include(":core:security")
include(":channels:telegram-bot")

