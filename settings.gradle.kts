pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        maven("https://jitpack.io")
    }
}

rootProject.name = "cloudstream-windows"

include(":library")
project(":library").projectDir = file("android-reference/library")

include(":common")
project(":common").projectDir = file("common")

include(":android-stubs")
project(":android-stubs").projectDir = file("android-stubs")

include(":plugin-runtime")
project(":plugin-runtime").projectDir = file("plugin-runtime")

include(":player-abstraction")
project(":player-abstraction").projectDir = file("player-abstraction")

include(":desktop-app")
project(":desktop-app").projectDir = file("desktop-app")

include(":sandbox")
project(":sandbox").projectDir = file("plugin-sandbox")
