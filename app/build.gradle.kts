import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // Needed for @Serializable classes' generated .serializer() - data/listen/Protocol.kt.
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

/**
 * Release signing credentials.
 *
 * Read from `keystore.properties` at the repo root, which is gitignored. The
 * values are never logged, never echoed into a build failure message, and the
 * file itself is never committed - only this loader is.
 *
 * When the file is missing the RELEASE BUILD HARD FAILS. That is deliberate:
 * Gradle's default is to emit `app-release-unsigned.apk`, which looks exactly
 * like a release artifact but cannot be installed and is not what was asked
 * for. Failing loudly is the only safe outcome. Debug builds are untouched and
 * keep working with no keystore at all.
 */
val releaseSigningFile = rootProject.file("keystore.properties")
val releaseSigning = Properties().apply {
    if (releaseSigningFile.exists()) {
        releaseSigningFile.inputStream().use { load(it) }
    }
}
val hasReleaseSigning = releaseSigning.getProperty("storeFile") != null &&
        releaseSigning.getProperty("keyAlias") != null

android {
    namespace = "nebula.music"
    compileSdk = 37

    defaultConfig {
        applicationId = "nebula.music"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "2.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                // storeFile is resolved against the repo root, so the property may
                // be an absolute path or relative to the project.
                storeFile = rootProject.file(releaseSigning.getProperty("storeFile"))
                storePassword = releaseSigning.getProperty("storePassword")
                keyAlias = releaseSigning.getProperty("keyAlias")
                keyPassword = releaseSigning.getProperty("keyPassword")
                // v1 + v2 + v3: v3 matters for Android 9+ key-rotation support.
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        // android.util.Log is unmocked by default and the repository's diag()
        // calls it, so every live-network test died on a stub before it reached
        // the network. Return defaults instead of throwing.
        unitTests.isReturnDefaultValues = true
    }
}

/**
 * Fail the build - rather than quietly emit `app-release-unsigned.apk` - when a
 * release task is requested without signing credentials. An unsigned APK is
 * byte-for-byte the sort of file someone would grab, upload and call "the
 * release"; it cannot be installed, so shipping it wastes everyone's time.
 *
 * Checked against the task graph so DEBUG builds never need a keystore.
 */
gradle.taskGraph.whenReady {
    val wantsRelease = allTasks.any { it.name.contains("Release", ignoreCase = true) }
    if (wantsRelease && !hasReleaseSigning) {
        throw GradleException(
            "Release signing credentials not found.\n" +
                    "Create keystore.properties at the repo root with:\n" +
                    "  storeFile=<path to .jks>\n" +
                    "  storePassword=<password>\n" +
                    "  keyAlias=<alias>\n" +
                    "  keyPassword=<password>\n" +
                    "The file is gitignored. Debug builds do not need it."
        )
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.media3.exoplayer)
    // Media3 cache for offline playback
    implementation("androidx.media3:media3-datasource:1.6.1")
    // Room for local song database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    // Room codegen for androidTest too, so the v1 migration fixture in
    // PlaylistMigrationTest gets a generated _Impl. Test-only, never shipped.
    kspAndroidTest(libs.androidx.room.compiler)
    // DataStore for theme + tab order persistence
    implementation(libs.androidx.datastore.preferences)
    // WorkManager for background download of updates
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    // On-device English translation for the lyric sub-line. Verified against the
    // live APIs that none of our lyrics providers ship translations, so the
    // English is produced on the phone. Models download per language on first
    // use (~30MB), then work offline; no key, no server, lyrics never leave.
    implementation("com.google.mlkit:translate:17.0.3")
    // ponytail: pinned to media3 catalog version 1.6.1; skipped media3-ui (no video widgets)
    implementation("androidx.media3:media3-session:1.6.1")
    // ponytail: pinned to innertubex's own stack (ktor 3.5.2 / serialization 1.11.0)
    implementation("com.github.MetrolistGroup.innertubex:innertubex:0.7.1")
    implementation("io.ktor:ktor-client-core:3.5.2")
    implementation("io.ktor:ktor-client-okhttp:3.5.2")
    implementation("io.ktor:ktor-client-content-negotiation:3.5.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.5.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("io.coil-kt.coil3:coil-compose:3.2.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.2.0")
    // The reorderable library is GONE, not merely unused. Reorder is up/down arrows: the
    // drag gesture was tried twice, could not be verified off-device, and a gesture that
    // silently does nothing is worse than a visible button. Drag is coming back only as
    // part of a real fix, so this dependency returns with it rather than sitting in the
    // build unused.
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// Modern Kotlin compiler options (replaces deprecated kotlinOptions)
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Force Kotlin stdlib to match the project's Kotlin version
configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-stdlib:2.4.10")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.4.10")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.4.10")
    }
}
