package com.balatromp.nativebridge.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.OutputStreamWriter
import rikka.shizuku.Shizuku

object ConfigManager {

    private const val FILE_NAME = "Multiplayer.jkr"
    private const val BALATRO_PACKAGE = "com.playstack.balatro.android"
    private const val BALATRO_GAME_PATH = "Android/data/$BALATRO_PACKAGE/files/save/game"
    const val SHIZUKU_REQUEST_CODE = 1001

    // ── Strategy 1: SAF (Storage Access Framework) ──────────────────────
    fun injectServerUrl(context: Context, directoryUri: Uri, serverUrl: String): Boolean {
        val rootDoc = DocumentFile.fromTreeUri(context, directoryUri) ?: return false
        val jkrFile = rootDoc.findFile(FILE_NAME) ?: return false

        return try {
            val content = context.contentResolver.openInputStream(jkrFile.uri)?.use {
                it.bufferedReader().readText()
            } ?: return false

            val newContent = replaceServerUrl(content, serverUrl)

            context.contentResolver.openOutputStream(jkrFile.uri, "wt")?.use { output ->
                OutputStreamWriter(output).use { writer ->
                    writer.write(newContent)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // ── Strategy 2: Direct file I/O (requires MANAGE_EXTERNAL_STORAGE) ─
    fun injectServerUrlDirect(serverUrl: String): Boolean {
        val jkrFile = File(getBalatroGameDir(), FILE_NAME)
        if (!jkrFile.exists()) return false

        return try {
            val content = jkrFile.readText()
            val newContent = replaceServerUrl(content, serverUrl)
            jkrFile.writeText(newContent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // ── Strategy 3: Shizuku (ADB-level, bypasses Scoped Storage) ───────
    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (_: Exception) {
            false
        }
    }

    fun hasShizukuPermission(): Boolean {
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
    }

    fun requestShizukuPermission() {
        if (isShizukuAvailable() && !hasShizukuPermission()) {
            Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
        }
    }

    @Suppress("DiscouragedPrivateApi")
    private fun shizukuNewProcess(cmd: Array<String>): Process {
        // Shizuku.newProcess is private since 13.1.2, use reflection (deprecated but still works)
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        )
        m.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return m.invoke(null, cmd, null, null) as Process
    }

    fun injectViaShizuku(serverUrl: String): Boolean {
        if (!isShizukuAvailable() || !hasShizukuPermission()) return false
        val jkrPath = File(getBalatroGameDir(), FILE_NAME).absolutePath
        return try {
            val readProcess = shizukuNewProcess(arrayOf("cat", jkrPath))
            readProcess.waitFor()
            if (readProcess.exitValue() != 0) return false
            val content = readProcess.inputStream.bufferedReader().readText()
            if (content.isEmpty()) return false
            val newContent = replaceServerUrl(content, serverUrl)
            val writeProcess = shizukuNewProcess(arrayOf("sh", "-c", "cat > \"$jkrPath\""))
            writeProcess.outputStream.write(newContent.toByteArray())
            writeProcess.outputStream.close()
            writeProcess.waitFor()
            writeProcess.exitValue() == 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
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
        } catch (_: Exception) { false }
    }
    fun isDirectFileExists(): Boolean = File(getBalatroGameDir(), FILE_NAME).exists()
    fun isShizukuFileExists(): Boolean {
        if (!isShizukuAvailable() || !hasShizukuPermission()) return false
        return try {
            val p = shizukuNewProcess(arrayOf("sh", "-c", "test -f \"${File(getBalatroGameDir(), FILE_NAME).absolutePath}\" && echo ok"))
            p.waitFor()
            p.exitValue() == 0 && p.inputStream.bufferedReader().readText().contains("ok")
        } catch (_: Exception) { false }
    }
    fun isConfigReady(context: Context, directoryUri: Uri?, method: String): Boolean {
        return when (method) {
            "shizuku" -> isShizukuFileExists()
            "direct" -> isDirectFileExists()
            else -> if (directoryUri != null) isSafFileExists(context, directoryUri) else false
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────
    fun getBalatroGameDir(): File {
        return File(Environment.getExternalStorageDirectory(), BALATRO_GAME_PATH)
    }

    fun canAccessDirectly(): Boolean {
        val gameDir = getBalatroGameDir()
        return gameDir.exists() && gameDir.canRead() && gameDir.canWrite()
    }

    fun hasStorageManagerPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    fun requestStorageManagerPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        }
    }

    fun getServerUrlFromFile(context: Context, directoryUri: Uri): String? {
        val rootDoc = DocumentFile.fromTreeUri(context, directoryUri) ?: return null
        val jkrFile = rootDoc.findFile(FILE_NAME) ?: return null

        return try {
            context.contentResolver.openInputStream(jkrFile.uri)?.use {
                val content = it.bufferedReader().readText()
                val regex = Regex("""\["server_url"\]\s*=\s*"([^"]*)"""")
                regex.find(content)?.groupValues?.get(1)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun replaceServerUrl(content: String, serverUrl: String): String {
        val regex = Regex("""(\["server_url"\]\s*=\s*")[^"]*(")""")
        return regex.replace(content) {
            "${it.groupValues[1]}$serverUrl${it.groupValues[2]}"
        }
    }
}
