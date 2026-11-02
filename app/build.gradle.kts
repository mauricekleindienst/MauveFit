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
        versionCode = 19
        versionName = "1.14.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // No release key yet: sign with the debug key so the APK installs directly.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
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
    implementation(libs.coil.network)
    implementation(libs.coil.gif)
    implementation(libs.gifdecoder)
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
