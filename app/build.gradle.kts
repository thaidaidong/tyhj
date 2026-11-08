plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.example.btwifi"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.example.btwifi"
        minSdk = 26
        targetSdk = 26   // giữ 26 để dùng được API Wi-Fi/Bluetooth cũ trên Android 8
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint { abortOnError = false; checkReleaseBuilds = false }
}
dependencies {
    implementation("org.nanohttpd:nanohttpd:2.3.1")
}
