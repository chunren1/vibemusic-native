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
        versionCode = 97
        versionName = "2.0.19-ai"
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
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Lint 门禁（CI release.yml 的 "Lint (release gate)" 步骤跑 lintRelease）。
    // 2026-10-22 首次接入时实测 80 errors / 50 warnings（历史债，如 NewApi），直接开必红。
    // 策略：历史问题一次性快照进 lint-baseline.xml；abortOnError 保持 true，
    // 因此**新增**的 error 立刻中断构建，旧债不阻塞发版。
    // 修掉旧债后请跑 `./gradlew updateLintBaseline` 收缩基线，不要手工编辑该文件。
    lint {
        baseline = file("lint-baseline.xml")
        abortOnError = true
        checkReleaseBuilds = true
        // GradleDependency 只报"有新版本可用"，而且把**旧版本号写进 message**——
        // baseline 按 message 匹配，所以每次依赖升级都会让整批条目失效、冒出成堆
        // 新 warning（2026-10-29 升 media3 1.9.4 后实测 8 条）。上游发新版也会同样失效。
        // 版本升级在本仓库由 AGENTS.md（工具链冻结）+ `scripts/verify-deps.sh`
        // （断言 minCompileSdk ≤ compileSdk）管，不靠 lint 提示；关掉换取确定性的 lint 输出。
        disable += "GradleDependency"
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
    implementation(libs.compose.material.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)
    implementation(libs.media3.common)
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.bugly.crashreport)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
