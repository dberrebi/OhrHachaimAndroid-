import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// The Play upload key. It is never committed: `keystore.properties` and `*.jks` are in .gitignore.
// Without the file, release builds are unsigned, as before; see play/README.md to sign one.
val uploadKey = rootProject.file("keystore.properties").takeIf { it.isFile }?.let { file ->
    Properties().apply { file.inputStream().use { load(it) } }
}

android {
    namespace = "app.ohrhachaim.android"
    compileSdk = 36
    // Pinned so a clean SDK needs only the packages in the README and CI. Without it, AGP picks its
    // own default (35.0.0) and downloads or fails on it.
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "app.ohrhachaim.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (uploadKey != null) {
            create("upload") {
                storeFile = rootProject.file(uploadKey.getProperty("storeFile"))
                storePassword = uploadKey.getProperty("storePassword")
                keyAlias = uploadKey.getProperty("keyAlias")
                keyPassword = uploadKey.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (uploadKey != null) signingConfig = signingConfigs.getByName("upload")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // The database is stored compressed in the APK (about 4.6 MB instead of 17.6 MB). The app
    // streams it into private storage on first launch, so it is never read in place.
    sourceSets.named("main") {
        assets.srcDir(rootProject.layout.projectDirectory.dir("data"))
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.sqlite)
    implementation(libs.androidx.sqlite.bundled)

    implementation(platform(libs.compose.bom))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.core.ktx)

    testImplementation(libs.junit)
}

tasks.named("preBuild").configure {
    dependsOn(rootProject.tasks.named("verifyDatabase"))
}
