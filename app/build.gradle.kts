plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.cyk666.vibemusic"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cyk666.vibemusic"
        minSdk = 26
        targetSdk = 35
        versionCode = 26
        versionName = "1.0.22-ai"
    }

    val wantsRelease = gradle.startParameter.taskNames.any {
        it.contains("Release", ignoreCase = true)
    }
    val releaseKeystorePassword = System.getenv("VIBEMUSIC_KEYSTORE_PASSWORD")
        ?: if (wantsRelease) throw GradleException(
            "VIBEMUSIC_KEYSTORE_PASSWORD env not set: " +
                "export it with the release keystore password before assembling a release build"
        ) else "debug-placeholder-only"
    signingConfigs {
        create("release") {
            storeFile = file(
                System.getenv("VIBEMUSIC_KEYSTORE_PATH")
                    ?: "/home/user/.dsh/keystores/vibemusic-native-release.jks"
            )
            storePassword = releaseKeystorePassword
            keyAlias = "vibemusic"
            keyPassword = releaseKeystorePassword
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            isShrinkResources = false
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
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)
    implementation(libs.media3.common)
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.moshi)
    implementation(libs.coil.compose)
    implementation(libs.datastore.preferences)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
