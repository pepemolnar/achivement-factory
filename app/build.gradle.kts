import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // No org.jetbrains.kotlin.android plugin — AGP 9.0+ built-in Kotlin support replaces it
    // (see the top-level build.gradle.kts comment for the version-pin rationale).
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// Release signing reads from a gitignored keystore.properties at the project root (never
// committed — see .gitignore). On a clean checkout without that file, the release build type
// below simply configures without a signing config rather than failing at configuration time,
// so `assembleRelease` still produces an unsigned APK instead of breaking the build.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val hasReleaseSigning = keystorePropertiesFile.exists()

android {
    namespace = "com.achievementfactory"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.achievementfactory"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
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

    // No explicit `kotlin { jvmToolchain(17) }` — that requests Gradle's toolchain service to
    // locate a literal JDK 17 install (auto-provisioning is not configured here), which fails
    // even though the build JVM (JDK 21, see gradle.properties) is fully capable of compiling
    // Java/Kotlin sources targeting 17 via compileOptions above.

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // PeriodWatcher calls ContextCompat.registerReceiver / ContextCompat.RECEIVER_NOT_EXPORTED
    // directly, so androidx.core is a first-order dependency of this module. It used to be present
    // only transitively (via activity-compose/lifecycle): a future version bump that dropped that
    // edge would break the build, or — worse — resolve an older `core` whose registerReceiver
    // overload behaves differently on API 33+, which is a security-relevant API to be vague about.
    implementation(libs.androidx.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.process)
    implementation(libs.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(composeBom)
    testImplementation(libs.junit)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.testing)
    testImplementation(libs.androidx.test.core)
    // Robolectric supports Compose semantics-tree assertions directly on the JVM (no device/
    // emulator needed) — used for the streak-badge hidden-at-zero behavior test (D-08).
    testImplementation(libs.compose.ui.test.junit4)

    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
}
