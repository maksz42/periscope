plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.maksz42.periscope"
    compileSdk = 36

    enableKotlin = false

    defaultConfig {
        applicationId = "com.maksz42.periscope"
        minSdk = 8
        // TODO api 35
        // forces edge-to-edge
        targetSdk = 34
        versionCode = 21
        versionName = "0.21"
        resValue("string", "app_version_name", versionName!!)

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
        resValues = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
}

dependencies {
    compileOnly(libs.annotation.jvm)
    implementation(libs.conscrypt.android)
//    implementation(libs.appcompat)
//    testImplementation(libs.junit)
//    androidTestImplementation(libs.ext.junit)
//    androidTestImplementation(libs.espresso.core)
}