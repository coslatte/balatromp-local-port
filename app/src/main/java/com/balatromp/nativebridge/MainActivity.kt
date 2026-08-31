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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.balatromp.nativebridge.service.MultiplayerService
import com.balatromp.nativebridge.ui.*
import com.balatromp.nativebridge.ui.theme.BalatroNativeBridgeTheme
import com.balatromp.nativebridge.util.ConfigManager
import com.balatromp.nativebridge.util.EmbeddedMod
import com.balatromp.nativebridge.util.ModManager
import com.balatromp.nativebridge.util.NetworkUtils
import com.balatromp.nativebridge.util.ShizukuShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

private const val PREFS_NAME = "balatro_bridge"
private const val KEY_GAME_FOLDER_URI = "game_folder_uri"
private const val KEY_ACCESS_METHOD = "access_method"
private const val KEY_MODS_URI = "balatro_mods_uri"
private const val GITHUB_URL = "https://github.com/coslatte/balatromp-local-port"

private fun Context.toast(resId: Int, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, resId, duration).show()
}

private fun Context.toast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}

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
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    var selectedUri by remember { mutableStateOf(prefs.getString(KEY_GAME_FOLDER_URI, null)?.let { Uri.parse(it) }) }
    var hostIp by remember { mutableStateOf("") }
    var isHostMode by remember { mutableStateOf(true) }
    var isBusy by remember { mutableStateOf(false) }
    var showAdbDialog by remember { mutableStateOf(false) }
    var showManual by remember { mutableStateOf(false) }
    var adbInstructions by remember { mutableStateOf("") }
    var accessMethod by remember { mutableStateOf(prefs.getString(KEY_ACCESS_METHOD, ConfigManager.ACCESS_SAF) ?: ConfigManager.ACCESS_SAF) }
    var shizukuPermissionGranted by remember { mutableStateOf(ShizukuShell.hasPermission()) }
    var showDetails by remember { mutableStateOf(false) }
    var modsFolderUri by remember { mutableStateOf(prefs.getString(KEY_MODS_URI, null)?.let { Uri.parse(it) }) }
    var modsBusy by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val listener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            shizukuPermissionGranted = grantResult == PackageManager.PERMISSION_GRANTED
            if (shizukuPermissionGranted) {
                accessMethod = ConfigManager.ACCESS_SHIZUKU
                prefs.edit().putString(KEY_ACCESS_METHOD, ConfigManager.ACCESS_SHIZUKU).apply()
                context.toast(R.string.toast_shizuku_granted)
            } else {
                context.toast(R.string.toast_shizuku_denied)
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        onDispose { Shizuku.removeRequestPermissionResultListener(listener) }
    }

    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            prefs.edit().putString(KEY_GAME_FOLDER_URI, uri.toString()).putString(KEY_ACCESS_METHOD, ConfigManager.ACCESS_SAF).apply()
            selectedUri = uri
            accessMethod = ConfigManager.ACCESS_SAF
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val storagePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            accessMethod = ConfigManager.ACCESS_DIRECT
            prefs.edit().putString(KEY_ACCESS_METHOD, ConfigManager.ACCESS_DIRECT).apply()
            context.toast(R.string.toast_storage_granted)
        }
    }
    val modsFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            prefs.edit().putString(KEY_MODS_URI, uri.toString()).apply()
            modsFolderUri = uri
        }
    }

    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
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
        if (!ShizukuShell.isAvailable()) {
            context.toast(R.string.toast_shizuku_not_running, Toast.LENGTH_LONG)
            return
        }
        if (!ShizukuShell.hasPermission()) {
            ShizukuShell.requestPermission()
        } else {
            accessMethod = ConfigManager.ACCESS_SHIZUKU
            prefs.edit().putString(KEY_ACCESS_METHOD, ConfigManager.ACCESS_SHIZUKU).apply()
            shizukuPermissionGranted = true
        }
    }

    fun copyToClipboard(text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("ip", text))
        context.toast(R.string.copied)
    }

    fun injectConfig(serverUrl: String) {
        isBusy = true
        scope.launch {
            val success = withContext(Dispatchers.IO) {
                ConfigManager.injectServerUrlBestEffort(context, selectedUri, serverUrl)
            }
            isBusy = false
            if (success) launchBalatro(context)
            else {
                adbInstructions = ConfigManager.generateAdbInstructions(serverUrl)
                showAdbDialog = true
            }
        }
    }

    fun installMod(mod: EmbeddedMod?) {
        modsBusy = mod?.id ?: MODS_BUSY_ALL
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                if (mod == null) ModManager.installAllMods(context, modsFolderUri)
                else ModManager.installMod(context, modsFolderUri, mod)
            }
            modsBusy = null
            val message = if (ok) {
                if (mod == null) context.getString(R.string.mods_install_success)
                else "${mod.displayName} ${context.getString(R.string.mods_installed)}"
            } else {
                context.getString(R.string.mods_install_failed)
            }
            context.toast(message)
        }
    }

    val localIp = remember { NetworkUtils.getLocalIpAddress(context) }
    val hasAnyAccess = selectedUri != null ||
        accessMethod == ConfigManager.ACCESS_DIRECT ||
        (accessMethod == ConfigManager.ACCESS_SHIZUKU && shizukuPermissionGranted)
    var isVerified by remember { mutableStateOf<Boolean?>(null) }
    var checking by remember { mutableStateOf(false) }
    LaunchedEffect(hasAnyAccess, accessMethod, selectedUri, shizukuPermissionGranted) {
        if (!hasAnyAccess) {
            isVerified = null
            checking = false
            return@LaunchedEffect
        }
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
            IntroCard(onOpenManual = { showManual = true })
            StatusCard(
                hasAnyAccess = hasAnyAccess,
                checking = checking,
                isVerified = isVerified,
                accessMethod = accessMethod,
                hasSafUri = selectedUri != null,
                showDetails = showDetails,
                onToggleDetails = { showDetails = !showDetails }
            )
            if (showDetails) {
                Spacer(Modifier.height(10.dp))
                DetailsCard(localIp = localIp, accessMethod = accessMethod, hasSafUri = selectedUri != null)
            }
            Spacer(Modifier.height(16.dp))

            if (!hasAnyAccess) {
                OnboardingSection(
                    onSelectFolder = { folderLauncher.launch(null) },
                    onRequestShizuku = { requestShizuku() },
                    onRequestStorage = { requestStoragePermission() }
                )
            } else {
                ModeSection(
                    isHostMode = isHostMode,
                    localIp = localIp,
                    hostIp = hostIp,
                    onSelectMode = { isHostMode = it },
                    onHostIpChange = { hostIp = it },
                    onCopyIp = { copyToClipboard(it) }
                )
                Spacer(Modifier.height(20.dp))
                PlayButton(
                    isHostMode = isHostMode,
                    isBusy = isBusy,
                    enabled = !isBusy && (isHostMode || hostIp.isNotBlank()),
                    onClick = {
                        val targetIp = if (isHostMode) localIp else hostIp.ifBlank { null }
                        if (targetIp == null) {
                            context.toast(R.string.error_invalid_ip)
                        } else {
                            if (isHostMode) {
                                ensureNotificationPermission()
                                ContextCompat.startForegroundService(context, Intent(context, MultiplayerService::class.java))
                            }
                            injectConfig("http://$targetIp:${MultiplayerService.SERVER_PORT}")
                        }
                    }
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = {
                        prefs.edit().remove(KEY_GAME_FOLDER_URI).remove(KEY_ACCESS_METHOD).apply()
                        selectedUri = null
                        accessMethod = ConfigManager.ACCESS_SAF
                    }) { Text(stringResource(R.string.btn_change_folder), style = MaterialTheme.typography.labelSmall) }
                    TextButton(onClick = { requestShizuku() }) { Text(stringResource(R.string.btn_shizuku_setup), style = MaterialTheme.typography.labelSmall) }
                }
            }

            ModsSection(
                modsFolderUri = modsFolderUri,
                modsBusy = modsBusy,
                showDetails = showDetails,
                onChooseFolder = { modsFolderLauncher.launch(null) },
                onInstallAll = { installMod(null) },
                onInstallMod = { installMod(it) }
            )
            Footer(onOpenGitHub = { openGitHub(context) })
        }
    }

    if (showManual) ManualDialog(onDismiss = { showManual = false })
    if (showAdbDialog) {
        AdbDialog(
            instructions = adbInstructions,
            onDismiss = { showAdbDialog = false },
            onGrantStorage = {
                showAdbDialog = false
                requestStoragePermission()
            },
            onSetupShizuku = {
                showAdbDialog = false
                requestShizuku()
            }
        )
    }
}

private fun launchBalatro(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(ConfigManager.BALATRO_PACKAGE)
    if (intent != null) context.startActivity(intent)
    else context.toast(R.string.error_balatro_not_found)
}

private fun openGitHub(context: Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)))
    } catch (_: Exception) {
    }
}
