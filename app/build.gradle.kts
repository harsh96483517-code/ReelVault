plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.reelvault.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.reelvault.app"
        minSdk = 26
        targetSdk = 35
        versionCode = (project.findProperty("appCode") as String?)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("appVersion") as String?) ?: "1.0"
        // GitHub repo ("owner/name") used by the in-app update check; set by CI via -PgithubRepo=
        buildConfigField("String", "UPDATE_REPO", "\"${project.findProperty("githubRepo") ?: ""}\"")
    }

    // One fixed key for every build so in-app updates install over the old version.
    signingConfigs {
        create("reelvault") {
            storeFile = file("reelvault.keystore")
            storePassword = "reelvault123"
            keyAlias = "reelvault"
            keyPassword = "reelvault123"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("reelvault")
        }
        debug {
            signingConfig = signingConfigs.getByName("reelvault")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.documentfile:documentfile:1.0.1")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-video:2.7.0")

    implementation("dev.chrisbanes.haze:haze:1.0.2")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
}
