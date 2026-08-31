package com.balatromp.nativebridge.util

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.OutputStreamWriter

object ConfigManager {

    private const val TAG = "ConfigManager"
    private const val FILE_NAME = "Multiplayer.jkr"
    const val BALATRO_PACKAGE = "com.playstack.balatro.android"
    private const val BALATRO_GAME_PATH = "Android/data/$BALATRO_PACKAGE/files/save/game"

    const val ACCESS_SAF = "saf"
    const val ACCESS_DIRECT = "direct"
    const val ACCESS_SHIZUKU = "shizuku"

    private val SERVER_URL_REGEX = Regex("""(\["server_url"\]\s*=\s*")([^"]*)(")""")

    // ── Strategy 1: SAF (Storage Access Framework) ──────────────────────
    fun injectServerUrl(context: Context, directoryUri: Uri, serverUrl: String): Boolean {
        val rootDoc = DocumentFile.fromTreeUri(context, directoryUri) ?: return false
        val jkrFile = rootDoc.findFile(FILE_NAME) ?: return false

        return try {
            val content = context.contentResolver.openInputStream(jkrFile.uri)?.use {
                it.bufferedReader().readText()
            } ?: return false

            context.contentResolver.openOutputStream(jkrFile.uri, "wt")?.use { output ->
                OutputStreamWriter(output).use { it.write(replaceServerUrl(content, serverUrl)) }
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "SAF inject failed", e)
            false
        }
    }

    // ── Strategy 2: Direct file I/O (requires MANAGE_EXTERNAL_STORAGE) ─
    fun injectServerUrlDirect(serverUrl: String): Boolean {
        val jkrFile = File(getBalatroGameDir(), FILE_NAME)
        if (!jkrFile.exists()) return false

        return try {
            jkrFile.writeText(replaceServerUrl(jkrFile.readText(), serverUrl))
            true
        } catch (e: Exception) {
            Log.w(TAG, "Direct inject failed", e)
            false
        }
    }

    // ── Strategy 3: Shizuku (ADB-level, bypasses Scoped Storage) ───────
    fun injectViaShizuku(serverUrl: String): Boolean {
        if (!ShizukuShell.isAvailable() || !ShizukuShell.hasPermission()) return false
        val jkrPath = File(getBalatroGameDir(), FILE_NAME).absolutePath
        return try {
            val readProcess = ShizukuShell.runCommand("cat", jkrPath)
            readProcess.waitFor()
            if (readProcess.exitValue() != 0) return false
            val content = readProcess.inputStream.bufferedReader().readText()
            if (content.isEmpty()) return false
            val writeProcess = ShizukuShell.runShell("cat > \"$jkrPath\"")
            writeProcess.outputStream.use { it.write(replaceServerUrl(content, serverUrl).toByteArray()) }
            writeProcess.waitFor()
            writeProcess.exitValue() == 0
        } catch (e: Exception) {
            Log.w(TAG, "Shizuku inject failed", e)
            false
        }
    }

    fun injectServerUrlBestEffort(context: Context, directoryUri: Uri?, serverUrl: String): Boolean {
        if (directoryUri != null && injectServerUrl(context, directoryUri, serverUrl)) return true
        if (ShizukuShell.isAvailable() && ShizukuShell.hasPermission() && injectViaShizuku(serverUrl)) return true
        if (hasStorageManagerPermission() && injectServerUrlDirect(serverUrl)) return true
        return false
    }

    // ── Strategy 4: Generate ADB command for manual copy ────────────────
    fun generateAdbInstructions(serverUrl: String): String {
        return """
            |If automatic access failed, connect your phone to a PC and run:
            |
            |1. Generate the file content first (app will save it)
            |2. Then run:
            |   adb push <path_to_file> /sdcard/$BALATRO_GAME_PATH/$FILE_NAME
            |
            |Or edit the file manually at:
            |   /sdcard/$BALATRO_GAME_PATH/$FILE_NAME
            |
            |Set this value:
            |   ["server_url"] = "$serverUrl"
        """.trimMargin()
    }

    // ── Validation ─────────────────────────────────────────────────────
    fun isSafFileExists(context: Context, directoryUri: Uri): Boolean {
        return try {
            val root = DocumentFile.fromTreeUri(context, directoryUri) ?: return false
            root.findFile(FILE_NAME)?.exists() == true
        } catch (_: Exception) {
            false
        }
    }

    fun isDirectFileExists(): Boolean = File(getBalatroGameDir(), FILE_NAME).exists()

    fun isShizukuFileExists(): Boolean {
        if (!ShizukuShell.isAvailable() || !ShizukuShell.hasPermission()) return false
        return ShizukuShell.runShellOk("test -f \"${File(getBalatroGameDir(), FILE_NAME).absolutePath}\"")
    }

    fun isConfigReady(context: Context, directoryUri: Uri?, method: String): Boolean {
        return when (method) {
            ACCESS_SHIZUKU -> isShizukuFileExists()
            ACCESS_DIRECT -> isDirectFileExists()
            else -> if (directoryUri != null) isSafFileExists(context, directoryUri) else false
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────
    fun getBalatroGameDir(): File = File(Environment.getExternalStorageDirectory(), BALATRO_GAME_PATH)

    fun getGameConfigPath(): String = File(getBalatroGameDir(), FILE_NAME).absolutePath

    fun hasStorageManagerPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    private fun replaceServerUrl(content: String, serverUrl: String): String =
        SERVER_URL_REGEX.replace(content) { "${it.groupValues[1]}$serverUrl${it.groupValues[3]}" }
}
