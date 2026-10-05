plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.neumusic.player"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.neumusic.player"
        minSdk = 26
        targetSdk = 35   // Android 15+ 只有 targetSdk>=35 才真正边到边（否则系统强加状态栏色块）
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 1
        versionName = "0.1.0"

        ndk {
            // Matching Rust/JNI artifacts are shipped for phones and both emulator architectures.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }
    }

    sourceSets.getByName("main") {
        java.srcDir("helpernext/kotlin")
        jniLibs.srcDir("helpernext/jniLibs")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }
}

// media3-session 会拉进 kotlin-stdlib 2.2.10，而本工程编译器是 Kotlin 2.0.21
// （metadata 版本不兼容，直接编译失败）。把 stdlib 钉回编译器同版本。
configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-stdlib:2.0.21")
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("junit:junit:4.13.2")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    // 前台播放服务（mediaPlayback 类型）+ 媒体通知/锁屏控制
    implementation("androidx.media3:media3-session:1.11.1")
}
