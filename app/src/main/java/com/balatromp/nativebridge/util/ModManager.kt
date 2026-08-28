package com.balatromp.nativebridge.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import com.github.junrar.Junrar
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import rikka.shizuku.Shizuku

data class EmbeddedMod(
    val id: String,
    val displayName: String,
    val description: String,
    val rarFolderName: String, // folder/file name inside rar
    val isZip: Boolean
)

object ModManager {
    val embeddedMods = listOf(
        EmbeddedMod("smods", "Steamodded (smods)", "Base requerida — contiene lovely/", "smods", false),
        EmbeddedMod("balatro_multiplayer", "Balatro Multiplayer", "Multijugador LAN", "BalatroMultiplayer.zip", true),
        EmbeddedMod("silk_touch", "Silk Touch", "Controles táctiles", "SilkTouch.zip", true),
    )

    private const val RAR_ASSET = "mods/balatromp-mods.rar"
    private const val BALATRO_EXTERNAL_PATH = "Balatro/ASET/Mods"

    fun getExternalModsDir(): File = File(Environment.getExternalStorageDirectory(), BALATRO_EXTERNAL_PATH)

    // ── RAR extraction to cache ──────────────────────────────────────
    private fun extractRarToCache(context: Context): File {
        val cacheRar = File(context.cacheDir, "balatromp-mods.rar")
        if (!cacheRar.exists() || cacheRar.length() == 0L) {
            context.assets.open(RAR_ASSET).use { input ->
                FileOutputStream(cacheRar).use { out -> input.copyTo(out) }
            }
        }
        val outDir = File(context.cacheDir, "rar_extract")
        if (outDir.exists()) outDir.deleteRecursively()
        outDir.mkdirs()
        Junrar.extract(cacheRar, outDir)
        // unzip inner zips to folders
        for (zipName in listOf("BalatroMultiplayer.zip", "SilkTouch.zip")) {
            val zipFile = File(outDir, zipName)
            if (zipFile.exists()) {
                val targetName = zipName.removeSuffix(".zip")
                val targetDir = File(outDir, targetName)
                targetDir.mkdirs()
                zipFile.inputStream().use { input -> unzipToFile(input, targetDir) }
            }
        }
        return outDir
    }

    private fun unzipToFile(input: java.io.InputStream, destDir: File) {
        ZipInputStream(input).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(destDir, entry.name)
                if (!outFile.canonicalPath.startsWith(destDir.canonicalPath)) { zis.closeEntry(); entry = zis.nextEntry; continue }
                if (entry.isDirectory) outFile.mkdirs()
                else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { out -> zis.copyTo(out) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    // ── Helpers to check/install single mod folder ───────────────────
    private fun getModSourceFolder(cacheExtractDir: File, mod: EmbeddedMod): File {
        return when (mod.id) {
            "smods" -> File(cacheExtractDir, "smods")
            "balatro_multiplayer" -> File(cacheExtractDir, "BalatroMultiplayer")
            "silk_touch" -> File(cacheExtractDir, "SilkTouch")
            else -> File(cacheExtractDir, mod.rarFolderName)
        }
    }

    // ── SAF ──────────────────────────────────────────────────────────
    fun ensureModsFolderViaSaf(context: Context, balatroTreeUri: Uri): DocumentFile? {
        var current = DocumentFile.fromTreeUri(context, balatroTreeUri) ?: return null
        for (segment in listOf("ASET", "Mods")) {
            var next = current.findFile(segment)
            if (next == null || !next.exists()) next = current.createDirectory(segment)
            if (next == null) return null
            current = next
        }
        return current
    }

    fun installViaSaf(context: Context, balatroTreeUri: Uri, mod: EmbeddedMod): Boolean {
        val modsDir = ensureModsFolderViaSaf(context, balatroTreeUri) ?: return false
        return try {
            val cacheExtract = extractRarToCache(context)
            val src = getModSourceFolder(cacheExtract, mod)
            if (!src.exists()) return false
            // destination is Mods/<modFolder> e.g. Mods/smods, Mods/BalatroMultiplayer
            val destName = when (mod.id) {
                "smods" -> "smods"
                "balatro_multiplayer" -> "BalatroMultiplayer"
                "silk_touch" -> "SilkTouch"
                else -> mod.id
            }
            // delete old if exists
            modsDir.findFile(destName)?.delete()
            val destDoc = modsDir.createDirectory(destName) ?: return false
            copyFileToSaf(src, destDoc, context)
            true
        } catch (e: Exception) { e.printStackTrace(); false }
    }

    fun installAllViaSaf(context: Context, balatroTreeUri: Uri): Boolean {
        var ok = true
        for (mod in embeddedMods) if (!installViaSaf(context, balatroTreeUri, mod)) ok = false
        return ok
    }

    private fun copyFileToSaf(src: File, destDoc: DocumentFile, context: Context) {
        if (src.isDirectory) {
            for (child in src.listFiles() ?: emptyArray()) {
                if (child.isDirectory) {
                    var sub = destDoc.findFile(child.name)
                    if (sub == null || !sub.exists()) sub = destDoc.createDirectory(child.name)!!
                    copyFileToSaf(child, sub, context)
                } else {
                    destDoc.findFile(child.name)?.delete()
                    val newFile = destDoc.createFile("application/octet-stream", child.name) ?: continue
                    context.contentResolver.openOutputStream(newFile.uri)?.use { out ->
                        child.inputStream().use { it.copyTo(out) }
                    }
                }
            }
        } else {
            destDoc.findFile(src.name)?.delete()
            val newFile = destDoc.createFile("application/octet-stream", src.name) ?: return
            context.contentResolver.openOutputStream(newFile.uri)?.use { out ->
                src.inputStream().use { it.copyTo(out) }
            }
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
            val cacheExtract = extractRarToCache(context)
            val src = getModSourceFolder(cacheExtract, mod)
            if (!src.exists()) return false
            val modsDir = ensureModsFolderDirect()
            val destName = when (mod.id) {
                "smods" -> "smods"
                "balatro_multiplayer" -> "BalatroMultiplayer"
                "silk_touch" -> "SilkTouch"
                else -> mod.id
            }
            val dest = File(modsDir, destName)
            if (dest.exists()) dest.deleteRecursively()
            src.copyRecursively(dest, overwrite = true)
            true
        } catch (e: Exception) { e.printStackTrace(); false }
    }

    fun installAllViaDirect(context: Context): Boolean {
        var ok = true
        for (mod in embeddedMods) if (!installViaDirect(context, mod)) ok = false
        return ok
    }

    // ── Shizuku ──────────────────────────────────────────────────────
    @Suppress("DiscouragedPrivateApi")
    private fun shizukuNewProcess(cmd: Array<String>): Process {
        val m = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
        m.isAccessible = true
        @Suppress("UNCHECKED_CAST") return m.invoke(null, cmd, null, null) as Process
    }

    fun ensureModsFolderViaShizuku(): Boolean {
        return try {
            val p = shizukuNewProcess(arrayOf("sh", "-c", "mkdir -p \"${getExternalModsDir().absolutePath}\" && echo ok"))
            p.waitFor(); p.exitValue() == 0
        } catch (_: Exception) { false }
    }

    fun installViaShizuku(context: Context, mod: EmbeddedMod): Boolean {
        if (!ConfigManager.isShizukuAvailable() || !ConfigManager.hasShizukuPermission()) return false
        return try {
            val cacheExtract = extractRarToCache(context)
            val src = getModSourceFolder(cacheExtract, mod)
            if (!src.exists()) return false
            ensureModsFolderViaShizuku()
            val destName = when (mod.id) {
                "smods" -> "smods"
                "balatro_multiplayer" -> "BalatroMultiplayer"
                "silk_touch" -> "SilkTouch"
                else -> mod.id
            }
            val destPath = File(getExternalModsDir(), destName).absolutePath
            // copy via cp -r
            val p = shizukuNewProcess(arrayOf("sh", "-c", "rm -rf \"$destPath\" && mkdir -p \"$destPath\" && cp -r \"${src.absolutePath}\"/* \"$destPath\"/ && cp -r \"${src.absolutePath}\"/.* \"$destPath\"/ 2>/dev/null; echo ok"))
            // simpler fallback: cp -r src dest
            // if src is directory, we want its contents inside dest, so use cp -r src/* dest/
            // For safety, also handle hidden files
            p.waitFor()
            // fallback if above failed (empty), try direct cp of folder
            if (p.exitValue() != 0) {
                val p2 = shizukuNewProcess(arrayOf("sh", "-c", "rm -rf \"$destPath\" && cp -r \"${src.absolutePath}\" \"$destPath\" && echo ok"))
                p2.waitFor()
                return p2.exitValue() == 0
            }
            true
        } catch (e: Exception) { e.printStackTrace(); false }
    }

    fun installAllViaShizuku(context: Context): Boolean {
        var ok = true
        for (mod in embeddedMods) if (!installViaShizuku(context, mod)) ok = false
        return ok
    }
}
