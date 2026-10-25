import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.moviebox.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.moviebox.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 17
        versionName = "0.12.2"

        ndk {
            // arm64-v8a covers real devices, x86_64 covers the emulator.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    // The release signing key never lives in the repository. The properties
    // are read from `keystore.properties` (gitignored) or from the
    // environment, so CI can sign with a secret.
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties()
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
    }
    val releaseStoreFile = keystoreProperties.getProperty("storeFile")
        ?: System.getenv("MOVIEBOX_STORE_FILE")
    val releaseStorePassword = keystoreProperties.getProperty("storePassword")
        ?: System.getenv("MOVIEBOX_STORE_PASSWORD")
    val releaseKeyAlias = keystoreProperties.getProperty("keyAlias")
        ?: System.getenv("MOVIEBOX_KEY_ALIAS")
    val releaseKeyPassword = keystoreProperties.getProperty("keyPassword")
        ?: System.getenv("MOVIEBOX_KEY_PASSWORD")
    val hasReleaseKey = listOf(
        releaseStoreFile,
        releaseStorePassword,
        releaseKeyAlias,
        releaseKeyPassword,
    ).all { !it.isNullOrBlank() }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
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
            if (hasReleaseKey) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
            // The debug-only Open URL hook lives in src/debug. A separate id
            // lets that build install beside the release one and keeps the
            // two variants' data apart.
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.datasource.okhttp)
    // Live TV channels are HLS streams.
    implementation(libs.androidx.media3.exoplayer.hls)

    implementation(libs.coil.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    testImplementation(libs.junit)
    // Virtual-time tests for wall-clock player timers; matches runtime coroutines.
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
}

/**
 * Builds the Rust core into `src/main/jniLibs/<abi>/libmoviebox_android.so`.
 *
 * Requires the Android NDK and `cargo-ndk`:
 *   cargo install cargo-ndk
 *   rustup target add aarch64-linux-android
 *
 * Enable it with `-PmovieboxBuildRust=true` (or set the property in
 * `gradle.properties`) so that a plain `./gradlew assembleDebug` does not fail
 * on machines without an NDK.
 */
val cargoNdk = "cargo-ndk"
val rustTarget = "aarch64-linux-android"
val rustProfile = "release"

val buildRustLibrary by tasks.registering(Exec::class) {
    group = "moviebox"
    description = "Cross-compiles the Rust core into jniLibs with cargo-ndk."
    workingDir = rootProject.file("..")
    doFirst {
        file("src/main/jniLibs").mkdirs()
    }
    commandLine(
        cargoNdk,
        "-t", "arm64-v8a",
        "-o", file("src/main/jniLibs").absolutePath,
        "build",
        "--release",
        // The package selection belongs to cargo, so it goes after the
        // cargo-ndk options.
        "-p", "moviebox-android",
    )
}

if (project.findProperty("movieboxBuildRust") == "true") {
    tasks.named("preBuild") {
        dependsOn(buildRustLibrary)
    }
}
