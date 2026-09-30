plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.nebula"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.nebula"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    buildFeatures {
        compose = true
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