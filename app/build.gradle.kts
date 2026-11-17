plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("io.github.takahirom.roborazzi")
}

android {
    namespace = "com.kithandkeen.qonverse"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kithandkeen.qonverse"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "0.3.1"
        buildConfigField("String", "UPDATE_URL", "\"https://raw.githubusercontent.com/aadvark/Qonverse/main/docs/version.json\"")
    }

    // Preview builds are signed with the Qonverse preview key so in-app updates install over each other.
    // Point QONVERSE_KEYSTORE (+ _PASSWORD, _ALIAS, _KEY_PASSWORD) at it; without it, the debug key is used.
    signingConfigs {
        create("preview") {
            val ks = System.getenv("QONVERSE_KEYSTORE")
            if (ks != null && file(ks).exists()) {
                storeFile = file(ks)
                storePassword = System.getenv("QONVERSE_KEYSTORE_PASSWORD") ?: "android"
                keyAlias = System.getenv("QONVERSE_KEY_ALIAS") ?: "androiddebugkey"
                keyPassword = System.getenv("QONVERSE_KEY_PASSWORD") ?: "android"
            } else {
                initWith(getByName("debug"))
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Use your own upload key (and turn off the in-app updater) for Google Play.
            signingConfig = signingConfigs.getByName("preview")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    lint { checkReleaseBuilds = false }
    testOptions { unitTests { isIncludeAndroidResources = true } }
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("qassets"))
}

// Bundle the repository's sample packs and the AI prompt into the app.
val copyQonverseAssets by tasks.registering(Copy::class) {
    from(rootProject.file("packs")) { into("packs") }
    from(rootProject.file("prompts/pack-generator-prompt.md"))
    into(layout.buildDirectory.dir("qassets"))
}
tasks.named("preBuild") { dependsOn(copyQonverseAssets) }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Screenshot tests (run on the JVM with Robolectric; not part of the app)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.39.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.39.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
