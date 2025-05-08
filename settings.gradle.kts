enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        val kotlinVersion = "1.9.22" // <-- Verifica y actualiza esta versión a la última estable
        kotlin("jvm") version kotlinVersion
        kotlin("android") version kotlinVersion apply false
        // ... otros plugins
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PassioAgogo"
include(":androidApp")
include(":shared")
include(":web")
