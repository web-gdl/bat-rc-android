import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "org.batrc.controller"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.batrc.controller"
        minSdk = 23
        targetSdk = 35
        // En GitHub Actions el número de ejecución sube en cada compilación,
        // así cada APK nuevo se instala encima del anterior.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "1.1.0"
    }

    signingConfigs {
        create("release") {
            val privateKeystore = System.getenv("BATRC_KEYSTORE")
            if (privateKeystore != null) {
                // Clave privada, configurada como secreto del repositorio.
                storeFile = file(privateKeystore)
                storePassword = System.getenv("BATRC_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("BATRC_KEY_ALIAS")
                keyPassword = System.getenv("BATRC_KEY_PASSWORD")
            } else {
                // Clave pública de pruebas incluida en el repositorio. Permite que
                // todas las compilaciones se actualicen entre sí, pero cualquiera
                // puede firmar con ella: no la uses para distribuir en una tienda.
                storeFile = rootProject.file("keystore/debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-rtsp:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")

    testImplementation("junit:junit:4.13.2")
}
