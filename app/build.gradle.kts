plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    compileSdk = 35

    defaultConfig {
        applicationId = "five.ec1cff.myinjector"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        ndk {
            abiFilters += "arm64-v8a"
        }
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
    androidResources.additionalParameters += listOf(
        "--allow-reserved-package-id",
        "--package-id",
        "0x68"
    )
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
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    compileOnly(libs.xposed.api)
    implementation(libs.ezxhelper)
    implementation(libs.dexkit)
    compileOnly(project(":hidden-api"))
}