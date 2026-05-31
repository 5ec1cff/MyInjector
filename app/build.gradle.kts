import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationParameters
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.gradle.tasks.PackageAndroidArtifact
import com.google.protobuf.gradle.id
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper
import java.io.FileInputStream
import java.nio.charset.StandardCharsets
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

fun String.execute(): String =
    Runtime.getRuntime().exec(split("\\s".toRegex()).toTypedArray())
        .let { proc ->
            proc.waitFor()
            val result = proc.inputStream.use {
                it.readBytes()
            }.toString(StandardCharsets.UTF_8).trim()
            proc.destroy()
            result
        }


val gitCommitCount = "git rev-list HEAD --count".execute().toInt()
val gitCommitHash = "git rev-parse --verify --short HEAD".execute()

android {
    compileSdk = 37
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
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "$gitCommitCount-$gitCommitHash"

        base.archivesName = "MyInjector-$versionName"
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin {
        jvmToolchain(21)
    }
    namespace = "io.github.a13e300.myinjector"
    packaging {
        resources {
            merges += "META-INF/xposed/*"
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

abstract class ClassVisitorFactory : AsmClassVisitorFactory<InstrumentationParameters.None> {
    override fun createClassVisitor(
        classContext: ClassContext,
        nextClassVisitor: ClassVisitor
    ): ClassVisitor {
        return ClassRemapper(nextClassVisitor, object : Remapper() {
            override fun map(name: String): String {
                if (name.startsWith("hidden/")) {
                    return name.substring(name.indexOf('/') + 1)
                }
                return name
            }
        })
    }

    override fun isInstrumentable(classData: ClassData): Boolean {
        return true
    }
}

androidComponents.onVariants { variant ->
    variant.instrumentation.transformClassesWith(
        ClassVisitorFactory::class.java, InstrumentationScope.PROJECT
    ) {}
}

dependencies {
    implementation(libs.dexkit)
    compileOnly(libs.androidx.annotation)
    compileOnly(project(":hidden-api"))
    implementation(libs.protobuf.kotlin)
    implementation(libs.protobuf.java)
    compileOnly(libs.protobuf.protoc)
    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)
}
