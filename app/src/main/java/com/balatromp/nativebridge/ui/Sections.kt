package com.balatromp.nativebridge.ui

import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.balatromp.nativebridge.R
import com.balatromp.nativebridge.service.MultiplayerService
import com.balatromp.nativebridge.util.ConfigManager
import com.balatromp.nativebridge.util.EmbeddedMod
import com.balatromp.nativebridge.util.ModManager
import com.balatromp.nativebridge.util.ShizukuShell

internal const val MODS_BUSY_ALL = "all"

@Composable
fun IntroCard(onOpenManual: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.how_it_works),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.manual_what_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onOpenManual, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.btn_manual))
            }
        }
    }
}

@Composable
private fun accessMethodLabel(accessMethod: String, hasSafUri: Boolean): String = when (accessMethod) {
    ConfigManager.ACCESS_DIRECT -> stringResource(R.string.access_direct)
    ConfigManager.ACCESS_SHIZUKU -> stringResource(R.string.access_shizuku)
    else -> if (hasSafUri) stringResource(R.string.access_saf) else stringResource(R.string.access_none)
}

@Composable
fun StatusCard(
    hasAnyAccess: Boolean,
    checking: Boolean,
    isVerified: Boolean?,
    accessMethod: String,
    hasSafUri: Boolean,
    showDetails: Boolean,
    onToggleDetails: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            val dotColor = when {
                !hasAnyAccess || checking -> MaterialTheme.colorScheme.outline
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
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (showDetails) {
                    Text(
                        stringResource(R.string.details_access, accessMethodLabel(accessMethod, hasSafUri)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            TextButton(onClick = onToggleDetails) {
                Text(
                    if (showDetails) stringResource(R.string.btn_details_hide) else stringResource(R.string.btn_details_show),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
fun DetailsCard(localIp: String?, accessMethod: String, hasSafUri: Boolean) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.details_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            DetailLine(stringResource(R.string.details_access, accessMethodLabel(accessMethod, hasSafUri)))
            DetailLine(stringResource(R.string.details_ip, localIp ?: stringResource(R.string.details_no)))
            DetailLine(
                stringResource(
                    R.string.details_server,
                    if (localIp != null) "http://$localIp:${MultiplayerService.SERVER_PORT}" else stringResource(R.string.details_inactive)
                )
            )
            val shizukuState = when {
                !ShizukuShell.isAvailable() -> stringResource(R.string.details_no)
                ShizukuShell.hasPermission() -> stringResource(R.string.details_granted)
                else -> stringResource(R.string.details_denied)
            }
            DetailLine(stringResource(R.string.details_shizuku, shizukuState))
            val storageState = if (ConfigManager.hasStorageManagerPermission()) stringResource(R.string.details_granted) else stringResource(R.string.details_denied)
            DetailLine(stringResource(R.string.details_storage, storageState))
            DetailLine(stringResource(R.string.details_config, ConfigManager.getGameConfigPath()))
        }
    }
}

@Composable
private fun DetailLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingSection(
    onSelectFolder: () -> Unit,
    onRequestShizuku: () -> Unit,
    onRequestStorage: () -> Unit
) {
    Text(stringResource(R.string.btn_select_folder), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    Text(
        stringResource(R.string.folder_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(14.dp))

    ElevatedCard(onClick = onSelectFolder, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("1 — " + stringResource(R.string.btn_select_folder), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.hint_grant_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Spacer(Modifier.height(10.dp))
    ElevatedCard(
        onClick = onRequestShizuku,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "2 — " + stringResource(R.string.btn_grant_shizuku),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                stringResource(R.string.hint_shizuku_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        OutlinedCard(onClick = onRequestStorage, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("3 — " + stringResource(R.string.btn_grant_full), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.option_a_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun ModeSection(
    isHostMode: Boolean,
    localIp: String?,
    hostIp: String,
    onSelectMode: (Boolean) -> Unit,
    onHostIpChange: (String) -> Unit,
    onCopyIp: (String) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ModeButton(
            selected = isHostMode,
            text = stringResource(R.string.radio_host),
            onClick = { onSelectMode(true) },
            modifier = Modifier.weight(1f)
        )
        ModeButton(
            selected = !isHostMode,
            text = stringResource(R.string.radio_client),
            onClick = { onSelectMode(false) },
            modifier = Modifier.weight(1f)
        )
    }
    Spacer(Modifier.height(16.dp))

    if (isHostMode) {
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.radio_host), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                Text(
                    localIp ?: stringResource(R.string.details_no),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    stringResource(R.string.host_port, MultiplayerService.SERVER_PORT),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(onClick = { localIp?.let(onCopyIp) }, enabled = localIp != null) {
                    Text(stringResource(R.string.btn_copy) + " IP")
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.manual_ip_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        OutlinedTextField(
            value = hostIp,
            onValueChange = onHostIpChange,
            label = { Text(stringResource(R.string.label_host_ip)) },
            placeholder = { Text("10.2.0.2") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.manual_flow_client_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ModeButton(selected: Boolean, text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) { Text(text) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(text) }
    }
}

@Composable
fun PlayButton(isHostMode: Boolean, isBusy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(54.dp)
    ) {
        if (isBusy) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(Modifier.width(12.dp))
        }
        Text(
            if (isHostMode) stringResource(R.string.btn_host_play) else stringResource(R.string.btn_join_play),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ModsSection(
    modsFolderUri: Uri?,
    modsBusy: String?,
    showDetails: Boolean,
    onChooseFolder: () -> Unit,
    onInstallAll: () -> Unit,
    onInstallMod: (EmbeddedMod) -> Unit
) {
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
                        Text(
                            modsFolderUri?.lastPathSegment?.substringAfterLast(':')
                                ?: stringResource(R.string.mods_not_selected),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(stringResource(R.string.mods_external_path), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = onChooseFolder) { Text(stringResource(R.string.mods_choose)) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onInstallAll,
                enabled = modsBusy == null,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (modsBusy == MODS_BUSY_ALL) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(stringResource(R.string.mods_install_all))
                }
            }
            Spacer(Modifier.height(12.dp))
            for (mod in ModManager.embeddedMods) {
                ModItem(
                    mod = mod,
                    isInstalling = modsBusy == mod.id,
                    enabled = modsBusy == null,
                    onInstall = { onInstallMod(mod) }
                )
            }
            if (showDetails) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Assets: " + ModManager.embeddedMods.joinToString { it.rarFolderName } +
                        " — destino: " + ModManager.getExternalModsDir().absolutePath,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ModItem(mod: EmbeddedMod, isInstalling: Boolean, enabled: Boolean, onInstall: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(mod.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(mod.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onInstall, enabled = enabled) {
                if (isInstalling) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.mods_install))
            }
        }
    }
}

@Composable
fun Footer(onOpenGitHub: () -> Unit) {
    Spacer(Modifier.height(28.dp))
    Divider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenGitHub),
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
