plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.maksz42.periscope"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.maksz42.periscope"
        minSdk = 8
        // TODO api 35
        // forces edge-to-edge
        targetSdk = 34
        versionCode = 7
        versionName = "0.7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
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
//    implementation(libs.appcompat)
//    testImplementation(libs.junit)
//    androidTestImplementation(libs.ext.junit)
//    androidTestImplementation(libs.espresso.core)
}