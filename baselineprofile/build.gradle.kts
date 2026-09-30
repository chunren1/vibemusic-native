plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.cyk666.vibemusic.baselineprofile"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 生成目标：主 app 模块（测 release 变体）。
    targetProjectPath = ":app"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        managedDevices {
            devices {
                // 本地无真机时的备选：./gradlew :baselineprofile:generateBaselineProfile
                // 会自动起 Pixel6 API 34 模拟器（需本机装 emulator + system-image）。
                // 有真机时直接连 USB 跑 connected 任务即可，不用改这里。
                create<com.android.build.api.dsl.ManagedVirtualDevice>("pixel6Api34") {
                    device = "Pixel 6"
                    apiLevel = 34
                    systemImageSource = "google"
                }
            }
        }
    }
}

dependencies {
    implementation(libs.test.ext.junit)
    implementation(libs.uiautomator)
    implementation(libs.benchmark.macro.junit4)
}
