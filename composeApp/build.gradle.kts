@file:OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)

import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        val commonMain by getting
        val sharedMain by creating {
            dependsOn(commonMain)
        }
        val androidMain by getting {
            dependsOn(sharedMain)
        }
        val desktopMain by getting {
            dependsOn(sharedMain)
        }

        sharedMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.bouncycastle.prov)
        }

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }

        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
        }

        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(compose.uiTest)
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

android {
    namespace = "com.crypttalk"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.crypttalk"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.crypttalk.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "CryptTalk"
            packageVersion = "1.0.0"
            description = "本地国密加密聊天工具"
            vendor = "CryptTalk"

            macOS {
                bundleID = "com.crypttalk.app"
            }
        }
    }
}
