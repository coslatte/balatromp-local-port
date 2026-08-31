package com.balatromp.nativebridge.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.balatromp.nativebridge.R

@Composable
fun ManualDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.how_it_works)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                DropdownSection(stringResource(R.string.manual_what_title), stringResource(R.string.manual_what_desc))
                DropdownSection(
                    stringResource(R.string.manual_flow_title),
                    stringResource(R.string.manual_flow_host_title) + ": " + stringResource(R.string.manual_flow_host_desc) +
                        "\n\n" + stringResource(R.string.manual_flow_client_title) + ": " + stringResource(R.string.manual_flow_client_desc)
                )
                DropdownSection(stringResource(R.string.manual_ip_title), stringResource(R.string.manual_ip_desc))
                DropdownSection(stringResource(R.string.manual_network_title), stringResource(R.string.manual_network_desc))
                DropdownSection(stringResource(R.string.manual_shizuku_title), stringResource(R.string.manual_shizuku_desc))
                DropdownSection(stringResource(R.string.manual_trouble_title), stringResource(R.string.manual_trouble_desc))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.manual_close)) } }
    )
}

@Composable
fun AdbDialog(
    instructions: String,
    onDismiss: () -> Unit,
    onGrantStorage: () -> Unit,
    onSetupShizuku: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_manual_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.dialog_manual_msg), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                DialogOption(stringResource(R.string.option_a_title), stringResource(R.string.option_a_desc))
                DialogOption(stringResource(R.string.option_b_title), stringResource(R.string.option_b_desc))
                DialogOption(stringResource(R.string.option_c_title), stringResource(R.string.option_c_desc))
                Spacer(Modifier.height(8.dp))
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        instructions,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Column {
                TextButton(onClick = onGrantStorage) { Text(stringResource(R.string.btn_grant)) }
                TextButton(onClick = onSetupShizuku) { Text(stringResource(R.string.btn_shizuku_setup)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_close)) } }
    )
}

@Composable
private fun DialogOption(title: String, desc: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Text(desc, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun DropdownSection(title: String, desc: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 6.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            Text(if (expanded) "▲" else "▼", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (expanded) {
            Text(desc, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            Divider(modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
