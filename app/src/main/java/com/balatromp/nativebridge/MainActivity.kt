package com.balatromp.nativebridge

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.balatromp.nativebridge.service.MultiplayerService
import com.balatromp.nativebridge.ui.theme.BalatroNativeBridgeTheme
import com.balatromp.nativebridge.util.ConfigManager
import com.balatromp.nativebridge.util.ModManager
import com.balatromp.nativebridge.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BalatroNativeBridgeTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("balatro_bridge", Context.MODE_PRIVATE) }

    var selectedUri by remember { mutableStateOf(prefs.getString("game_folder_uri", null)?.let { Uri.parse(it) }) }
    var hostIp by remember { mutableStateOf("") }
    var isHostMode by remember { mutableStateOf(true) }
    var isBusy by remember { mutableStateOf(false) }
    var showAdbDialog by remember { mutableStateOf(false) }
    var showManual by remember { mutableStateOf(false) }
    var adbInstructions by remember { mutableStateOf("") }
    var accessMethod by remember { mutableStateOf(prefs.getString("access_method", "saf") ?: "saf") }
    var shizukuPermissionGranted by remember { mutableStateOf(ConfigManager.hasShizukuPermission()) }
    var showDetails by remember { mutableStateOf(false) }
    var modsFolderUri by remember { mutableStateOf(prefs.getString("balatro_mods_uri", null)?.let { Uri.parse(it) }) }
    var modsBusy by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val listener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            shizukuPermissionGranted = grantResult == PackageManager.PERMISSION_GRANTED
            if (shizukuPermissionGranted) {
                accessMethod = "shizuku"
                prefs.edit().putString("access_method", "shizuku").apply()
                Toast.makeText(context, context.getString(R.string.toast_shizuku_granted), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, context.getString(R.string.toast_shizuku_denied), Toast.LENGTH_SHORT).show()
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        onDispose { Shizuku.removeRequestPermissionResultListener(listener) }
    }

    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            prefs.edit().putString("game_folder_uri", uri.toString()).putString("access_method", "saf").apply()
            selectedUri = uri
            accessMethod = "saf"
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val storagePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Environment.isExternalStorageManager()) {
            accessMethod = "direct"
            prefs.edit().putString("access_method", "direct").apply()
            Toast.makeText(context, context.getString(R.string.toast_storage_granted), Toast.LENGTH_SHORT).show()
        }
    }
    val modsFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            prefs.edit().putString("balatro_mods_uri", uri.toString()).apply()
            modsFolderUri = uri
        }
    }

    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            storagePermissionLauncher.launch(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            })
        }
    }
    fun requestShizuku() {
        if (!ConfigManager.isShizukuAvailable()) {
            Toast.makeText(context, context.getString(R.string.toast_shizuku_not_running), Toast.LENGTH_LONG).show()
            return
        }
        if (!ConfigManager.hasShizukuPermission()) ConfigManager.requestShizukuPermission()
        else {
            accessMethod = "shizuku"
            prefs.edit().putString("access_method", "shizuku").apply()
            shizukuPermissionGranted = true
        }
    }
    fun copyToClipboard(text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("ip", text))
        Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
    }
    fun injectConfig(serverUrl: String) {
        isBusy = true
        scope.launch {
            val success = withContext(Dispatchers.IO) {
                if (selectedUri != null && ConfigManager.injectServerUrl(context, selectedUri!!, serverUrl)) return@withContext true
                if (ConfigManager.isShizukuAvailable() && ConfigManager.hasShizukuPermission() && ConfigManager.injectViaShizuku(serverUrl)) return@withContext true
                if (ConfigManager.hasStorageManagerPermission() && ConfigManager.injectServerUrlDirect(serverUrl)) return@withContext true
                if (ConfigManager.injectViaShizuku(serverUrl)) return@withContext true
                false
            }
            isBusy = false
            if (success) launchBalatro(context) else {
                adbInstructions = ConfigManager.generateAdbInstructions(serverUrl)
                showAdbDialog = true
            }
        }
    }

    val localIp = remember { NetworkUtils.getLocalIpAddress(context) }
    val hasAnyAccess = selectedUri != null || accessMethod == "direct" || (accessMethod == "shizuku" && shizukuPermissionGranted)
    var isVerified by remember { mutableStateOf<Boolean?>(null) }
    var checking by remember { mutableStateOf(false) }
    LaunchedEffect(hasAnyAccess, accessMethod, selectedUri, shizukuPermissionGranted) {
        if (!hasAnyAccess) { isVerified = null; checking = false; return@LaunchedEffect }
        checking = true
        isVerified = withContext(Dispatchers.IO) {
            ConfigManager.isConfigReady(context, selectedUri, accessMethod)
        }
        checking = false
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                actions = {
                    TextButton(onClick = { showManual = true }, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                        Text(stringResource(R.string.btn_help))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Intro / Manual teaser
            ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.how_it_works), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.manual_what_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { showManual = true }, modifier = Modifier.align(Alignment.End)) {
                        Text(stringResource(R.string.btn_manual))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // Status card - simple, con validación real
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    val dotColor = when {
                        !hasAnyAccess -> MaterialTheme.colorScheme.outline
                        checking -> MaterialTheme.colorScheme.outline
                        isVerified == true -> Color(0xFF2E7D32)
                        else -> Color(0xFFC62828)
                    }
                    val dot = when {
                        !hasAnyAccess -> "○"
                        checking -> "◌"
                        else -> "●"
                    }
                    Text(dot, color = dotColor, fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                !hasAnyAccess -> stringResource(R.string.dialog_manual_msg)
                                checking -> stringResource(R.string.details_checking)
                                isVerified == true -> stringResource(R.string.details_ok) + " — " + stringResource(R.string.details_verified)
                                else -> stringResource(R.string.details_ok) + " — " + stringResource(R.string.details_not_verified)
                            },
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (showDetails) {
                            val label = when (accessMethod) {
                                "direct" -> stringResource(R.string.access_direct)
                                "shizuku" -> stringResource(R.string.access_shizuku)
                                else -> if (selectedUri != null) stringResource(R.string.access_saf) else stringResource(R.string.access_none)
                            }
                            Text(stringResource(R.string.details_access, label), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                    }
                    TextButton(onClick = { showDetails = !showDetails }) {
                        Text(if (showDetails) stringResource(R.string.btn_details_hide) else stringResource(R.string.btn_details_show), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (showDetails) {
                Spacer(Modifier.height(10.dp))
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.details_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        val accessLabel = when (accessMethod) {
                            "direct" -> stringResource(R.string.access_direct)
                            "shizuku" -> stringResource(R.string.access_shizuku)
                            else -> if (selectedUri != null) stringResource(R.string.access_saf) else stringResource(R.string.access_none)
                        }
                        Text(stringResource(R.string.details_access, accessLabel), style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        Text(stringResource(R.string.details_ip, localIp ?: stringResource(R.string.details_no)), style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        Text(stringResource(R.string.details_server, if (localIp != null) "http://$localIp:8788" else stringResource(R.string.details_inactive)), style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        val shizukuState = when {
                            !ConfigManager.isShizukuAvailable() -> stringResource(R.string.details_no)
                            ConfigManager.hasShizukuPermission() -> stringResource(R.string.details_granted)
                            else -> stringResource(R.string.details_denied)
                        }
                        Text(stringResource(R.string.details_shizuku, shizukuState), style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        val storageState = if (ConfigManager.hasStorageManagerPermission()) stringResource(R.string.details_granted) else stringResource(R.string.details_denied)
                        Text(stringResource(R.string.details_storage, storageState), style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        Text(stringResource(R.string.details_config, ConfigManager.getBalatroGameDir().absolutePath + "/Multiplayer.jkr"), style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            if (!hasAnyAccess) {
                // Onboarding - 3 options minimalista
                Text(stringResource(R.string.btn_select_folder), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.folder_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Spacer(Modifier.height(14.dp))

                ElevatedCard(onClick = { folderLauncher.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("1 — " + stringResource(R.string.btn_select_folder), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.hint_grant_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(10.dp))
                ElevatedCard(onClick = { requestShizuku() }, modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("2 — " + stringResource(R.string.btn_grant_shizuku), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(stringResource(R.string.hint_shizuku_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    OutlinedCard(onClick = { requestStoragePermission() }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("3 — " + stringResource(R.string.btn_grant_full), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.option_a_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isHostMode) {
                        Button(onClick = { isHostMode = true }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.radio_host)) }
                        OutlinedButton(onClick = { isHostMode = false }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.radio_client)) }
                    } else {
                        OutlinedButton(onClick = { isHostMode = true }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.radio_host)) }
                        Button(onClick = { isHostMode = false }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.radio_client)) }
                    }
                }
                Spacer(Modifier.height(16.dp))

                if (isHostMode) {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.radio_host), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(6.dp))
                            Text(localIp ?: "Unknown", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            Text("puerto 8788", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(10.dp))
                            FilledTonalButton(onClick = { localIp?.let { copyToClipboard(it) } }, enabled = localIp != null) {
                                Text(stringResource(R.string.btn_copy) + " IP")
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.manual_ip_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = hostIp, onValueChange = { hostIp = it },
                        label = { Text(stringResource(R.string.label_host_ip)) },
                        placeholder = { Text("10.2.0.2") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.manual_flow_client_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        val targetIp = if (isHostMode) localIp else hostIp.ifBlank { null }
                        if (targetIp == null) {
                            Toast.makeText(context, context.getString(R.string.error_invalid_ip), Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (isHostMode) {
                            ensureNotificationPermission()
                            ContextCompat.startForegroundService(context, Intent(context, MultiplayerService::class.java))
                        }
                        injectConfig("http://$targetIp:8788")
                    },
                    enabled = !isBusy && (isHostMode || hostIp.isNotBlank()),
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(if (isHostMode) stringResource(R.string.btn_host_play) else stringResource(R.string.btn_join_play), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = {
                        prefs.edit().remove("game_folder_uri").remove("access_method").apply()
                        selectedUri = null; accessMethod = "saf"
                    }) { Text(stringResource(R.string.btn_change_folder), style = MaterialTheme.typography.labelSmall) }
                    TextButton(onClick = { requestShizuku() }) { Text(stringResource(R.string.btn_shizuku_setup), style = MaterialTheme.typography.labelSmall) }
                }
            }

            // ── Mods ───────────────────────────────────────────────────
            Spacer(Modifier.height(20.dp))
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.mods_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.mods_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.mods_how), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.mods_folder_label), style = MaterialTheme.typography.labelMedium)
                                Text(if (modsFolderUri != null) modsFolderUri.toString().substringAfterLast("%3A").substringAfterLast("/") else stringResource(R.string.mods_not_selected), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stringResource(R.string.mods_external_path), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Button(onClick = { modsFolderLauncher.launch(null) }) { Text(stringResource(R.string.mods_choose)) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                modsBusy = "all"
                                scope.launch {
                                    val ok = withContext(Dispatchers.IO) {
                                        var allOk = true
                                        for (mod in ModManager.embeddedMods) {
                                            val res = when {
                                                modsFolderUri != null -> ModManager.installViaSaf(context, modsFolderUri!!, mod)
                                                ConfigManager.isShizukuAvailable() && ConfigManager.hasShizukuPermission() -> ModManager.installViaShizuku(context, mod)
                                                ConfigManager.hasStorageManagerPermission() -> ModManager.installViaDirect(context, mod)
                                                else -> false
                                            }
                                            if (!res) allOk = false
                                        }
                                        allOk
                                    }
                                    modsBusy = null
                                    Toast.makeText(context, if (ok) context.getString(R.string.mods_install_success) else context.getString(R.string.mods_install_failed), Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = modsBusy == null,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (modsBusy == "all") CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            else Text(stringResource(R.string.mods_install_all))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    for (mod in ModManager.embeddedMods) {
                        val isInstalling = modsBusy == mod.id
                        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(mod.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Text(mod.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        modsBusy = mod.id
                                        scope.launch {
                                            val ok = withContext(Dispatchers.IO) {
                                                when {
                                                    modsFolderUri != null -> ModManager.installViaSaf(context, modsFolderUri!!, mod)
                                                    ConfigManager.isShizukuAvailable() && ConfigManager.hasShizukuPermission() -> ModManager.installViaShizuku(context, mod)
                                                    ConfigManager.hasStorageManagerPermission() -> ModManager.installViaDirect(context, mod)
                                                    else -> false
                                                }
                                            }
                                            modsBusy = null
                                            Toast.makeText(context, if (ok) mod.displayName + " " + context.getString(R.string.mods_installed) else context.getString(R.string.mods_install_failed), Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = modsBusy == null
                                ) {
                                    if (isInstalling) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                    else Text(stringResource(R.string.mods_install))
                                }
                            }
                        }
                    }
                    if (showDetails) {
                        Spacer(Modifier.height(8.dp))
                        Text("Assets: mods/smods, BalatroMultiplayer.zip, SilkTouch.zip — destino: ${ModManager.getExternalModsDir().absolutePath}", style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().clickable {
                    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/coslatte/balatromp-local-port"))) } catch (_: Exception) {}
                },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.credit), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_github),
                    contentDescription = stringResource(R.string.credit_github_desc),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }

    if (showManual) {
        AlertDialog(
            onDismissRequest = { showManual = false },
            title = { Text(stringResource(R.string.how_it_works)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    DropdownSection(stringResource(R.string.manual_what_title), stringResource(R.string.manual_what_desc))
                    DropdownSection(stringResource(R.string.manual_flow_title), stringResource(R.string.manual_flow_host_title) + ": " + stringResource(R.string.manual_flow_host_desc) + "\n\n" + stringResource(R.string.manual_flow_client_title) + ": " + stringResource(R.string.manual_flow_client_desc))
                    DropdownSection(stringResource(R.string.manual_ip_title), stringResource(R.string.manual_ip_desc))
                    DropdownSection(stringResource(R.string.manual_network_title), stringResource(R.string.manual_network_desc))
                    DropdownSection(stringResource(R.string.manual_shizuku_title), stringResource(R.string.manual_shizuku_desc))
                    DropdownSection(stringResource(R.string.manual_trouble_title), stringResource(R.string.manual_trouble_desc))
                }
            },
            confirmButton = { TextButton(onClick = { showManual = false }) { Text(stringResource(R.string.manual_close)) } }
        )
    }

    if (showAdbDialog) {
        AlertDialog(
            onDismissRequest = { showAdbDialog = false },
            title = { Text(stringResource(R.string.dialog_manual_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.dialog_manual_msg), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.option_a_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.option_a_desc), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.option_b_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.option_b_desc), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.option_c_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.option_c_desc), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(8.dp))
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                        Text(adbInstructions, style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.padding(8.dp))
                    }
                }
            },
            confirmButton = {
                Column {
                    TextButton(onClick = { showAdbDialog = false; requestStoragePermission() }) { Text(stringResource(R.string.btn_grant)) }
                    TextButton(onClick = { showAdbDialog = false; requestShizuku() }) { Text(stringResource(R.string.btn_shizuku_setup)) }
                }
            },
            dismissButton = { TextButton(onClick = { showAdbDialog = false }) { Text(stringResource(R.string.btn_close)) } }
        )
    }
}

@Composable
private fun DropdownSection(title: String, desc: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 6.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            Text(if (expanded) "▲" else "▼", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (expanded) {
            Text(desc, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            Divider(modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

fun launchBalatro(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage("com.playstack.balatro.android")
    if (intent != null) context.startActivity(intent)
    else Toast.makeText(context, context.getString(R.string.error_balatro_not_found), Toast.LENGTH_SHORT).show()
}
