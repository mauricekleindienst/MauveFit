plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    // Unit tests run the pure logic on the JVM; Android calls they touch return defaults.
    testOptions { unitTests.isReturnDefaultValues = true }
    namespace = "dev.mauve.fit"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.mauve.fit"
        minSdk = 29
        targetSdk = 36
        versionCode = 56
        versionName = "1.23.3"
    }

    // The muscle map and the Everkinetic drawings live once in the repo, beside the iOS app.
    sourceSets["main"].assets.srcDir("../ios/MauveFit/Resources")

    // Google Play's upload key, only where it is set up (CI secrets or a local keystore): see
    // scripts/make-upload-key.sh. Without it the Play bundle keeps the debug key and only proves
    // that it builds; Play rejects it.
    val upload = System.getenv("MAUVE_UPLOAD_KEYSTORE")?.takeIf { file(it).exists() }
    signingConfigs {
        if (upload != null) create("upload") {
            storeFile = file(upload)
            storePassword = System.getenv("MAUVE_UPLOAD_STORE_PASSWORD")
            keyAlias = System.getenv("MAUVE_UPLOAD_KEY_ALIAS") ?: "upload"
            keyPassword = System.getenv("MAUVE_UPLOAD_KEY_PASSWORD")
        }
    }

    // Two ways to the phone: the GitHub releases, which update themselves (Updater), and Google
    // Play, which updates the app itself and does not allow an app to install its own updates.
    flavorDimensions += "store"
    productFlavors {
        create("github") {
            dimension = "store"
            buildConfigField("boolean", "SELF_UPDATE", "true")
        }
        create("play") {
            dimension = "store"
            buildConfigField("boolean", "SELF_UPDATE", "false")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // GitHub APKs keep the debug key of earlier releases, so installed apps can update.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        disable += setOf("NewerVersionAvailable", "OldTargetApi", "GradleDependency")
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.coil.compose)
    implementation(libs.health.connect)
    testImplementation(libs.junit)

    // Galaxy Watch sync over the Wear OS data layer.
    implementation(libs.wearable)
    implementation(libs.kotlinx.coroutines.play.services)

    // Home screen widget.
    implementation(libs.glance.appwidget)

    // Food barcode scanner and the Open Food Facts lookup.
    implementation(libs.code.scanner)
    implementation(libs.okhttp)
}

// The Play release is signed with the upload key; build types alone would give it the debug key.
androidComponents {
    onVariants(selector().withFlavor("store" to "play").withBuildType("release")) { variant ->
        android.signingConfigs.findByName("upload")?.let { variant.signingConfig.setConfig(it) }
    }
}
