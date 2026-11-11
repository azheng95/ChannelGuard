pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // XPosed API stubs
        maven("https://api.xposed.info/") {
            content { includeGroup("de.robv.android.xposed") }
        }
    }
}

rootProject.name = "ChannelGuard"
include(":app")
