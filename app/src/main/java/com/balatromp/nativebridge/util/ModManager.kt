package com.balatromp.nativebridge.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.github.junrar.Junrar
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

data class EmbeddedMod(
    val id: String,
    val displayName: String,
    val description: String,
    val rarFolderName: String, // folder/file name inside rar
    val installFolderName: String,
    val isZip: Boolean
)

object ModManager {

    private const val TAG = "ModManager"
    private const val RAR_ASSET = "mods/balatromp-mods.rar"
    private const val BALATRO_EXTERNAL_PATH = "Balatro/ASET/Mods"

    val embeddedMods = listOf(
        EmbeddedMod("smods", "Steamodded (smods)", "Base requerida — contiene lovely/", "smods", "smods", false),
        EmbeddedMod("balatro_multiplayer", "Balatro Multiplayer", "Multijugador LAN", "BalatroMultiplayer.zip", "BalatroMultiplayer", true),
        EmbeddedMod("silk_touch", "Silk Touch", "Controles táctiles", "SilkTouch.zip", "SilkTouch", true),
    )

    fun getExternalModsDir(): File = File(Environment.getExternalStorageDirectory(), BALATRO_EXTERNAL_PATH)

    fun installMod(context: Context, balatroTreeUri: Uri?, mod: EmbeddedMod): Boolean = when {
        balatroTreeUri != null -> installViaSaf(context, balatroTreeUri, mod)
        ShizukuShell.isAvailable() && ShizukuShell.hasPermission() -> installViaShizuku(context, mod)
        ConfigManager.hasStorageManagerPermission() -> installViaDirect(context, mod)
        else -> false
    }

    fun installAllMods(context: Context, balatroTreeUri: Uri?): Boolean =
        embeddedMods.fold(true) { ok, mod -> installMod(context, balatroTreeUri, mod) && ok }

    // ── RAR extraction to cache ──────────────────────────────────────
    private fun extractRarToCache(context: Context): File {
        val cacheRar = File(context.cacheDir, "balatromp-mods.rar")
        if (!cacheRar.exists() || cacheRar.length() == 0L) {
            context.assets.open(RAR_ASSET).use { input ->
                FileOutputStream(cacheRar).use { input.copyTo(it) }
            }
        }
        val outDir = File(context.cacheDir, "rar_extract")
        if (outDir.exists()) outDir.deleteRecursively()
        outDir.mkdirs()
        Junrar.extract(cacheRar, outDir)
        for (mod in embeddedMods.filter { it.isZip }) {
            val zipFile = File(outDir, mod.rarFolderName)
            if (!zipFile.exists()) continue
            val targetDir = File(outDir, mod.installFolderName)
            targetDir.mkdirs()
            zipFile.inputStream().use { input -> unzipToFile(input, targetDir) }
            zipFile.delete()
        }
        return outDir
    }

    private fun resolveModSource(context: Context, mod: EmbeddedMod): File? {
        val src = File(extractRarToCache(context), mod.installFolderName)
        return if (src.exists()) src else null
    }

    private fun unzipToFile(input: InputStream, destDir: File) {
        val destRoot = destDir.canonicalPath + File.separator
        ZipInputStream(input).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(destDir, entry.name)
                if (outFile.canonicalPath.startsWith(destRoot)) {
                    if (entry.isDirectory) outFile.mkdirs()
                    else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { out -> zis.copyTo(out) }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    // ── SAF ──────────────────────────────────────────────────────────
    fun ensureModsFolderViaSaf(context: Context, balatroTreeUri: Uri): DocumentFile? {
        var current = DocumentFile.fromTreeUri(context, balatroTreeUri) ?: return null
        for (segment in listOf("ASET", "Mods")) {
            val found = current.findFile(segment)
            val next = if (found != null && found.exists()) found
                else current.createDirectory(segment) ?: return null
            current = next
        }
        return current
    }

    fun installViaSaf(context: Context, balatroTreeUri: Uri, mod: EmbeddedMod): Boolean {
        return try {
            val modsDir = ensureModsFolderViaSaf(context, balatroTreeUri) ?: return false
            val src = resolveModSource(context, mod) ?: return false
            modsDir.findFile(mod.installFolderName)?.delete()
            val destDoc = modsDir.createDirectory(mod.installFolderName) ?: return false
            copyFileToSaf(src, destDoc, context)
            true
        } catch (e: Exception) {
            Log.w(TAG, "SAF install failed for ${mod.id}", e)
            false
        }
    }

    private fun copyFileToSaf(src: File, destDoc: DocumentFile, context: Context) {
        if (!src.isDirectory) {
            writeSafFile(src, destDoc, context)
            return
        }
        for (child in src.listFiles() ?: emptyArray()) {
            if (child.isDirectory) {
                val subDir = findOrCreateSafDir(destDoc, child.name) ?: continue
                copyFileToSaf(child, subDir, context)
            } else {
                writeSafFile(child, destDoc, context)
            }
        }
    }

    private fun findOrCreateSafDir(parent: DocumentFile, name: String?): DocumentFile? {
        if (name == null) return null
        val found = parent.findFile(name)
        if (found != null && found.exists()) return found
        return parent.createDirectory(name)
    }

    private fun writeSafFile(file: File, destDoc: DocumentFile, context: Context) {
        destDoc.findFile(file.name)?.delete()
        val target = destDoc.createFile("application/octet-stream", file.name) ?: return
        context.contentResolver.openOutputStream(target.uri)?.use { out ->
            file.inputStream().use { it.copyTo(out) }
        }
    }

    // ── Direct ───────────────────────────────────────────────────────
    fun ensureModsFolderDirect(): File {
        val dir = getExternalModsDir()
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun installViaDirect(context: Context, mod: EmbeddedMod): Boolean {
        return try {
            val src = resolveModSource(context, mod) ?: return false
            val dest = File(ensureModsFolderDirect(), mod.installFolderName)
            if (dest.exists()) dest.deleteRecursively()
            src.copyRecursively(dest, overwrite = true)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Direct install failed for ${mod.id}", e)
            false
        }
    }

    // ── Shizuku ──────────────────────────────────────────────────────
    fun ensureModsFolderViaShizuku(): Boolean =
        ShizukuShell.runShellOk("mkdir -p \"${getExternalModsDir().absolutePath}\"")

    fun installViaShizuku(context: Context, mod: EmbeddedMod): Boolean {
        if (!ShizukuShell.isAvailable() || !ShizukuShell.hasPermission()) return false
        return try {
            val src = resolveModSource(context, mod) ?: return false
            ensureModsFolderViaShizuku()
            val destPath = File(getExternalModsDir(), mod.installFolderName).absolutePath
            ShizukuShell.runShellOk(
                "rm -rf \"$destPath\" && cp -r \"${src.absolutePath}\" \"$destPath\""
            )
        } catch (e: Exception) {
            Log.w(TAG, "Shizuku install failed for ${mod.id}", e)
            false
        }
    }
}
