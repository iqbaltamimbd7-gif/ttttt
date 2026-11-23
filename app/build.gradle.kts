plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val facebookClientToken = providers.gradleProperty("facebookClientToken")
    .orElse(providers.environmentVariable("FACEBOOK_CLIENT_TOKEN"))
    .getOrElse("")
val escapedFacebookClientToken = facebookClientToken
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

android {
    namespace = "com.sayma.portal"
    compileSdk = 35

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.sayma.portal"
        minSdk = 23
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"
        buildConfigField("String", "FACEBOOK_CLIENT_TOKEN", "\"$escapedFacebookClientToken\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("com.facebook.android:facebook-login:18.0.3")
}
