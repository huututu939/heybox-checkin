import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.heybox.checkin"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.heybox.checkin"
        minSdk = 26
        targetSdk = 35
        versionCode = 26
        versionName = "1.8.0"
    }

    // 发布签名：keystore.properties 不进仓库，换机器时把 keystore 与属性文件一起带走，
    // 否则群友覆盖安装会因签名不一致而失败。
    val keystoreProps = Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // 先用不混淆的 release：WebView 里的官方设备指纹 SDK 走 JS 桥，
            // 混淆一旦漏 keep 就会在登录时才炸，没法在真机上悄悄回归。
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystoreProps.getProperty("storeFile") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    // AGP 9 在 Windows 上跑 lintVital 会锁住 lint-cache 里的 jar 导致 release 构建失败；
    // 这里关掉发版前的 lint 检查（CI 仍然跑单测 + debug 构建）。
    lint {
        checkReleaseBuilds = false
        // lintDebug 在 CI 里跑；有 Error 就失败，Warning 不影响出包
        abortOnError = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    // Compose 2026.11 起要求 compileSdk 37.1；本机与 CI 只装了 37.0，
    // 升 SDK 单独一步做，先把 BOM 留在 2025.12（Compose 1.12）。
    implementation(platform("androidx.compose:compose-bom:2025.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("io.github.kyant0:backdrop:2.0.1")
    implementation("io.github.kyant0:shapes:1.2.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
}
