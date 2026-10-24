import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().takeIf { keystorePropertiesFile.isFile }?.apply {
    keystorePropertiesFile.inputStream().use { load(it) }
}

fun keystoreValue(name: String, trim: Boolean = true): String {
    val raw = keystoreProperties?.getProperty(name).orEmpty()
    val value = if (trim) raw.trim() else raw
    if (value.isEmpty()) {
        throw GradleException(
            "keystore.properties has no $name. Run scripts/keystore.ps1. " +
                "It asks for a password, or generates a random one when it cannot prompt. " +
                "This repo does not store a password.",
        )
    }
    return value
}

android {
    namespace = "com.rerun.tv"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.rerun.tv"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (keystoreProperties != null) {
            create("release") {
                storeFile = rootProject.file(keystoreValue("storeFile"))
                storePassword = keystoreValue("storePassword", trim = false)
                keyAlias = keystoreValue("keyAlias")
                keyPassword = keystoreValue("keyPassword", trim = false)
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (keystoreProperties != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":core"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.okhttp)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    implementation(libs.security.crypto)
    implementation(libs.coil.compose)
    implementation(libs.coil.okhttp)
}

val checkReleaseKeystore = tasks.register("checkReleaseKeystore") {
    doLast {
        if (!keystorePropertiesFile.isFile) {
            throw GradleException(
                "Release builds need a signing key on this machine. Run .\\scripts\\keystore.ps1 or .\\scripts\\install.ps1. " +
                    "The script asks for a password, or generates a random one when it cannot prompt. " +
                    "rerun-release.jks and keystore.properties are gitignored. This repo does not store a password.",
            )
        }
    }
}
tasks.configureEach {
    if (name == "assembleRelease" || name == "packageRelease" || name == "bundleRelease") {
        dependsOn(checkReleaseKeystore)
    }
}
