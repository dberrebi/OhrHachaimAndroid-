plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    sourceSets.getByName("androidMain").kotlin.srcDir("src/shared/kotlin")
    sourceSets.getByName("jvmMain").kotlin.srcDir("src/shared/kotlin")
    sourceSets.named("commonMain") {
        dependencies {
            implementation(libs.androidx.sqlite)
            implementation(libs.androidx.sqlite.bundled)
        }
    }
    sourceSets.named("jvmTest") {
        dependencies {
            implementation(kotlin("test-junit"))
        }
    }
}

android {
    namespace = "app.ohrhachaim.core"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
