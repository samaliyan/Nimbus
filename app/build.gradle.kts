plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// CI passes these; a local build without them still works and is marked as a dev build.
val appVersionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
val appVersionName = System.getenv("VERSION_NAME") ?: "0.0.0-dev"
val coreVersion = System.getenv("CORE_VERSION") ?: "unknown"
val updateRepo = System.getenv("UPDATE_REPO") ?: "samaliyan/Nimbus"

val keystorePath: String? = System.getenv("SIGNING_KEYSTORE")

android {
    namespace = "com.samaliyan.nimbus"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.samaliyan.nimbus"
        // The Aether core is built for Android 10 (API 29) and later.
        minSdk = 29
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "CORE_VERSION", "\"$coreVersion\"")
        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                // Pasted secrets often carry a stray space or newline.
                val storePw = System.getenv("SIGNING_STORE_PASSWORD")?.trim()
                storePassword = storePw
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")?.trim()
                // The key is in a PKCS12 store, where the key password is the store password.
                keyPassword = storePw
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (keystorePath != null) signingConfigs.getByName("release")
            else signingConfigs.getByName("debug")
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            // The three ABIs the Aether project publishes Android builds for.
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    packaging {
        jniLibs {
            // The core, psiphon and lyrebird are executables shipped as lib*.so so Android
            // allows running them; they must be extracted to disk to be executed.
            useLegacyPackaging = true
            // These are finished programs, already stripped by their own builds.
            keepDebugSymbols += setOf("**/libaether.so", "**/libpsiphon.so", "**/liblyrebird.so")
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
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    testImplementation("junit:junit:4.13.2")
    // Android ships org.json in the platform; JVM unit tests need a real copy of it.
    testImplementation("org.json:json:20250517")
}
