plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    compileSdk = 33

    defaultConfig {
        applicationId = "five.ec1cff.myinjector"
        minSdk = 30
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"
    }
    buildFeatures {
        buildConfig = true
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                ("proguard-rules.pro")
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    namespace = "five.ec1cff.myinjector"
}

dependencies {
    compileOnly(libs.xposed.api)
    implementation(libs.ezxhelper)
    compileOnly(project(":hidden-api"))
}