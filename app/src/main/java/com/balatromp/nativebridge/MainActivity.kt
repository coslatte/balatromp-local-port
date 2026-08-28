package com.balatromp.nativebridge

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import androidx.core.content.ContextCompat
import com.balatromp.nativebridge.service.MultiplayerService
import com.balatromp.nativebridge.ui.theme.BalatroNativeBridgeTheme
import com.balatromp.nativebridge.util.ConfigManager
import com.balatromp.nativebridge.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("balatro_bridge", Context.MODE_PRIVATE) }

    var selectedUri by remember {
        mutableStateOf(prefs.getString("game_folder_uri", null)?.let { Uri.parse(it) })
    }
    var hostIp by remember { mutableStateOf("") }
    var isHostMode by remember { mutableStateOf(true) }
    var isBusy by remember { mutableStateOf(false) }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            prefs.edit().putString("game_folder_uri", uri.toString()).apply()
            selectedUri = uri
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
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
                    val targetIp = if (isHostMode) NetworkUtils.getLocalIpAddress(context) else hostIp.ifBlank { null }
                    if (targetIp == null) {
                        Toast.makeText(context, "Invalid IP Address", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    if (isHostMode) {
                        ensureNotificationPermission()
                        ContextCompat.startForegroundService(context, Intent(context, MultiplayerService::class.java))
                    }

                    val serverUrl = "http://$targetIp:8788"
                    isBusy = true
                    scope.launch {
                        val success = withContext(Dispatchers.IO) {
                            ConfigManager.injectServerUrl(context, selectedUri!!, serverUrl)
                        }
                        isBusy = false
                        if (success) {
                            launchBalatro(context)
                        } else {
                            Toast.makeText(context, "Failed to update Multiplayer.jkr", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = !isBusy && (isHostMode || hostIp.isNotBlank()),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (isHostMode) "Host & Play" else "Join & Play")
            }

            TextButton(
                onClick = {
                    prefs.edit().remove("game_folder_uri").apply()
                    selectedUri = null
                }
            ) {
                Text("Change folder", style = MaterialTheme.typography.bodySmall)
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
