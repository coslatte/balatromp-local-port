package com.balatromp.nativebridge.util

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.OutputStreamWriter

object ConfigManager {

    private const val FILE_NAME = "Multiplayer.jkr"

    fun injectServerUrl(context: Context, directoryUri: Uri, serverUrl: String): Boolean {
        val rootDoc = DocumentFile.fromTreeUri(context, directoryUri) ?: return false
        val jkrFile = rootDoc.findFile(FILE_NAME) ?: return false

        try {
            val content = context.contentResolver.openInputStream(jkrFile.uri)?.use { 
                it.bufferedReader().readText() 
            } ?: return false

            // Regex to find: ["server_url"] = "http://..."
            val regex = Regex("""(\["server_url"\]\s*=\s*")[^"]*(")""")
            val newContent = regex.replace(content) { 
                "${it.groupValues[1]}$serverUrl${it.groupValues[2]}" 
            }

            context.contentResolver.openOutputStream(jkrFile.uri, "wt")?.use { output ->
                OutputStreamWriter(output).use { writer ->
                    writer.write(newContent)
                }
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
