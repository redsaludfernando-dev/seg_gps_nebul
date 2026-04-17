plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "app.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        val wasmJsMain by getting

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.play.services.location)
            implementation(libs.maplibre.android)
            // Servidor local de tiles PMTiles (mapas offline)
            implementation(libs.ktor.server.cio)
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.cors)
        }

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(projects.shared)
        }

        wasmJsMain.dependencies {
            // Engine HTTP ya viene transitivo desde shared → ktor-client-js
        }
    }
}

// Forzar browser a versión compatible con compileSdk 35
configurations.all {
    resolutionStrategy.force("androidx.browser:browser:1.8.0")
}

android {
    namespace = "com.redsalud.seggpsnebul"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")
    sourceSets["main"].resources.srcDirs("src/commonMain/resources")

    defaultConfig {
        applicationId = "com.redsalud.seggpsnebul"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            storeFile     = file(System.getenv("KEYSTORE_PATH") ?: "keystore/release.jks")
            storePassword = System.getenv("KEYSTORE_PASS") ?: ""
            keyAlias      = System.getenv("KEY_ALIAS")     ?: "seggps"
            keyPassword   = System.getenv("KEY_PASS")      ?: ""
        }
    }

    buildTypes {
        debug {
            ndk { abiFilters += listOf("x86_64", "arm64-v8a") }
        }
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
