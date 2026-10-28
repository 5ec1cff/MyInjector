// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    }
}

plugins {
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
}

tasks.register("Delete", Delete::class) {
    delete(rootProject.buildDir)
}