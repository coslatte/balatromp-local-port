package com.balatromp.nativebridge

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.balatromp.nativebridge.service.MultiplayerService
import com.balatromp.nativebridge.ui.theme.BalatroNativeBridgeTheme
import com.balatromp.nativebridge.util.ConfigManager
import com.balatromp.nativebridge.util.NetworkUtils

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BalatroNativeBridgeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var hostIp by remember { mutableStateOf("") }
    var isHostMode by remember { mutableStateOf(true) }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            selectedUri = uri
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Balatro Native Bridge", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))

        if (selectedUri == null) {
            Button(onClick = { folderLauncher.launch(null) }) {
                Text("Select Balatro Game Folder")
            }
            Text(
                text = "Path: Android/data/com.playstack.balatro.android/files/save/game/",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = isHostMode, onClick = { isHostMode = true })
                Text("Host Mode")
                Spacer(modifier = Modifier.width(16.dp))
                RadioButton(selected = !isHostMode, onClick = { isHostMode = false })
                Text("Client Mode")
            }

            if (!isHostMode) {
                OutlinedTextField(
                    value = hostIp,
                    onValueChange = { hostIp = it },
                    label = { Text("Host IP Address") },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                val localIp = NetworkUtils.getLocalIpAddress(context) ?: "Unknown"
                Text("Your Local IP: $localIp")
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val targetIp = if (isHostMode) NetworkUtils.getLocalIpAddress(context) else hostIp
                    if (targetIp == null || targetIp == "Unknown") {
                        Toast.makeText(context, "Invalid IP Address", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    if (isHostMode) {
                        context.startService(Intent(context, MultiplayerService::class.java))
                    }

                    val serverUrl = "http://$targetIp:8788"
                    val success = ConfigManager.injectServerUrl(context, selectedUri!!, serverUrl)

                    if (success) {
                        launchBalatro(context)
                    } else {
                        Toast.makeText(context, "Failed to update Multiplayer.jkr", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isHostMode) "Host & Play" else "Join & Play")
            }
        }
    }
}

fun launchBalatro(context: android.content.Context) {
    val intent = context.packageManager.getLaunchIntentForPackage("com.playstack.balatro.android")
    if (intent != null) {
        context.startActivity(intent)
    } else {
        Toast.makeText(context, "Balatro app not found!", Toast.LENGTH_SHORT).show()
    }
}
