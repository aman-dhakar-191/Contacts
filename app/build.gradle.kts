plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Firebase (Analytics + Crashlytics) is only wired in when app/google-services.json is present,
// so a fork or a checkout without the file still builds; the Telemetry wrapper then does nothing.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

// CI passes these from the git tag (v1.2.3 -> 1.2.3, versionCode = major*1000000 + minor*1000 + patch).
val appVersionName = (findProperty("versionName") as String?) ?: "0.1.0"
val appVersionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1

android {
    namespace = "io.github.amandhakar.contacts"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.amandhakar.contacts"
        minSdk = 29
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        // CI sets KEYSTORE_FILE (+ password/alias) from repository secrets. Without them the build
        // falls back to the experimental keystore committed in keystore/ (see README).
        val envKeystore = System.getenv("KEYSTORE_FILE")
        val fallback = rootProject.file("keystore/experimental.jks")
        if (envKeystore != null) {
            create("release") {
                storeFile = file(envKeystore)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        } else if (fallback.exists()) {
            create("release") {
                storeFile = fallback
                storePassword = "experimental"
                keyAlias = "experimental"
                keyPassword = "experimental"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")
    // Firebase / Play services pull in androidx.fragment 1.1.0, which the release lint check rejects for the
    // ActivityResult APIs used in MainActivity. Pin a current version.
    implementation("androidx.fragment:fragment:1.8.5")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    // android.jar's org.json is only a stub in unit tests; the rules file code needs the real thing there
    testImplementation("org.json:json:20240303")
}
