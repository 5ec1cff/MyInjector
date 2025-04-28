import com.android.build.gradle.tasks.PackageAndroidArtifact
import com.google.protobuf.gradle.id
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.protobuf)
}

val keystorePropertiesFile: File = rootProject.file("keystore.properties")
val keystoreProperties = if (keystorePropertiesFile.exists() && keystorePropertiesFile.isFile) {
    Properties().apply {
        load(FileInputStream(keystorePropertiesFile))
    }
} else null

fun String.execute(currentWorkingDir: File = file("./")): String {
    val byteOut = ByteArrayOutputStream()
    project.exec {
        workingDir = currentWorkingDir
        commandLine = split("\\s".toRegex())
        standardOutput = byteOut
    }
    return String(byteOut.toByteArray()).trim()
}

val gitCommitCount = "git rev-list HEAD --count".execute().toInt()
val gitCommitHash = "git rev-parse --verify --short HEAD".execute()

android {
    compileSdk = 35
    signingConfigs {
        if (keystoreProperties != null) {
            create("release") {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
            }
        }
    }

    defaultConfig {
        applicationId = "io.github.a13e300.myinjector"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "$gitCommitCount-$gitCommitHash"
        setProperty("archivesBaseName", "MyInjector-$versionName")
        ndk {
            //noinspection ChromeOsAbiSupport
            abiFilters += "arm64-v8a"
        }
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
                ("proguard-rules.pro")
            )
            val releaseSig = signingConfigs.findByName("release")
            signingConfig = if (releaseSig != null) releaseSig else {
                println("use debug signing config")
                signingConfigs["debug"]
            }
        }
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    androidResources.additionalParameters += listOf(
        "--allow-reserved-package-id",
        "--package-id",
        "0x68"
    )
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    namespace = "io.github.a13e300.myinjector"
    packaging {
        resources {
            excludes += "**"
        }
    }
    // https://stackoverflow.com/a/77745844
    tasks.withType<PackageAndroidArtifact> {
        doFirst { appMetadata.asFile.orNull?.writeText("") }
    }

    lint {
        checkReleaseBuilds = false
    }
}

protobuf {
    protoc {
        artifact = libs.protobuf.protoc.get().toString()
    }

    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                id("java") {
                    option("lite")
                }
                id("kotlin") {
                    option("lite")
                }
            }
        }
    }
}

dependencies {
    compileOnly(libs.xposed.api)
    implementation(libs.dexkit)
    compileOnly(libs.androidx.annotation)
    compileOnly(project(":hidden-api"))
    implementation(libs.protobuf.kotlin)
    implementation(libs.protobuf.java)
    compileOnly(libs.protobuf.protoc)
}
