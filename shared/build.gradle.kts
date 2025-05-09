plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.androidLibrary)
}

buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        // ... otras dependencias
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        // Si usas un repositorio específico de JetBrains, podrías agregar algo como:
        // maven("https://maven.pkg.jetbrains.space/public/p/kotlin/p/runtime")
    }
}

kotlin {
    androidTarget()
    
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            //put your multiplatform dependencies here
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

