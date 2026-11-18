import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

val manifestFile = layout.projectDirectory.file("data/manifest.json")
val databaseFile = layout.projectDirectory.file("data/ohr_hachaim.sqlite")

tasks.register("verifyDatabase") {
    description = "Fail the build if data/ohr_hachaim.sqlite does not match data/manifest.json."
    inputs.file(manifestFile)
    inputs.file(databaseFile)
    doLast {
        val manifest = manifestFile.asFile.readText()
        val expected = Regex(""""sha256"\s*:\s*"([0-9a-fA-F]+)"""")
            .find(manifest)
            ?.groupValues
            ?.get(1)
            ?.lowercase()
            ?: error("data/manifest.json has no sha256")
        val digest = MessageDigest.getInstance("SHA-256").digest(databaseFile.asFile.readBytes())
        val actual = digest.joinToString("") { "%02x".format(it) }
        check(actual == expected) {
            "ohr_hachaim.sqlite SHA-256 is $actual, manifest expects $expected. " +
                "Copy the sqlite and manifest together from the iOS repo; do not edit the database here."
        }
    }
}

tasks.register("test") {
    description = "Unit tests for the reading calendar, search, and the Android app."
    dependsOn(":core:jvmTest", ":app:testDebugUnitTest")
}
