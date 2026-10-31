plugins {
    id("com.android.application")
}

android {
    namespace = "com.aegismirror.app"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "com.aegismirror.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "0.4.0-udp-lab"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint { abortOnError = true }
}
dependencies { testImplementation("junit:junit:4.13.2") }
