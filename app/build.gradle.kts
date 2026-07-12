plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.amitozalvo.nothingsuite"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.amitozalvo.nothingsuite"
        minSdk = 34
        targetSdk = 35
        versionCode = 6
        versionName = "0.3.3"
    }

    // Sideload signing: create signing/release.keystore (gitignored) with
    //   keytool -genkeypair -keystore signing/release.keystore \
    //     -alias nothingsuite -keyalg RSA -keysize 2048 -validity 10000
    // and put the passwords in env vars NOTHINGSUITE_STORE_PASSWORD /
    // NOTHINGSUITE_KEY_PASSWORD. Without a keystore, release falls back to
    // the debug key so assembleRelease still produces an installable APK.
    val sideloadKeystore = rootProject.file("signing/release.keystore")
    if (sideloadKeystore.exists()) {
        signingConfigs {
            create("release") {
                storeFile = sideloadKeystore
                storePassword = System.getenv("NOTHINGSUITE_STORE_PASSWORD")
                keyAlias = System.getenv("NOTHINGSUITE_KEY_ALIAS") ?: "nothingsuite"
                keyPassword = System.getenv("NOTHINGSUITE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (sideloadKeystore.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    }
}

dependencies {
    implementation(files("libs/glyph-matrix-sdk-2.0.aar"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
