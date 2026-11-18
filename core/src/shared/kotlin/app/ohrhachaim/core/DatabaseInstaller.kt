package app.ohrhachaim.core

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * Copies the bundled database out of the APK into private storage and checks its SHA-256
 * against the bundled `manifest.json`.
 *
 * A stamp file records the checksum of the installed copy. Whenever the stamp does not match the
 * manifest (first launch, or an app update that ships a new database), the database is copied
 * again, even when the new file has the same size as the old one: SQLite files grow in whole
 * pages, so a corrected text often keeps the size. The copy is verified before it replaces the
 * installed file, so a bad bundle never overwrites a good database.
 */
object DatabaseInstaller {
    const val DATABASE = "ohr_hachaim.sqlite"
    const val MANIFEST = "manifest.json"

    fun install(directory: File, openAsset: (String) -> InputStream): File {
        val out = File(directory, DATABASE)
        val stamp = File(directory, "$DATABASE.sha256")
        val manifest = openAsset(MANIFEST).bufferedReader().use { it.readText() }
        val expected = sha256In(manifest)
        val expectedBytes = bytesIn(manifest)
        if (out.isFile && out.length() == expectedBytes && stamp.isFile && stamp.readText().trim() == expected) {
            return out
        }
        directory.mkdirs()
        val part = File(directory, "$DATABASE.part")
        openAsset(DATABASE).use { input ->
            part.outputStream().use { output -> input.copyTo(output) }
        }
        val actual = sha256(part)
        if (actual != expected) {
            part.delete()
            error("Bundled $DATABASE SHA-256 is $actual, manifest expects $expected")
        }
        stamp.delete()
        if (out.exists()) out.delete()
        if (!part.renameTo(out)) {
            part.copyTo(out, overwrite = true)
            part.delete()
        }
        stamp.writeText(expected)
        return out
    }

    fun sha256In(manifest: String): String =
        Regex(""""sha256"\s*:\s*"([0-9a-fA-F]+)"""")
            .find(manifest)
            ?.groupValues
            ?.get(1)
            ?.lowercase()
            ?: error("$MANIFEST has no sha256")

    private fun bytesIn(manifest: String): Long =
        Regex(""""bytes"\s*:\s*(\d+)""")
            .find(manifest)
            ?.groupValues
            ?.get(1)
            ?.toLong()
            ?: error("$MANIFEST has no bytes")

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return hex(digest.digest())
    }

    /** Lowercase hex with ASCII digits, independent of the device language. */
    fun hex(bytes: ByteArray): String {
        val digits = "0123456789abcdef"
        return buildString(bytes.size * 2) {
            for (byte in bytes) {
                val value = byte.toInt() and 0xFF
                append(digits[value ushr 4])
                append(digits[value and 0x0F])
            }
        }
    }
}
