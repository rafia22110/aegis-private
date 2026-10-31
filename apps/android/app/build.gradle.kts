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
        versionCode = 2
        versionName = "0.2.0-local-core"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint { abortOnError = true }
}
dependencies { testImplementation("junit:junit:4.13.2") }
