package com.yadisksync.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = state.oauthToken,
                onValueChange = { viewModel.setOauthToken(it) },
                label = { Text("OAuth Token") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Oldest date to download", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    DatePickerField(
                        value = state.oldestDateMillis,
                        onValueChange = { viewModel.setOldestDate(it) }
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Sync interval: ${state.syncIntervalMinutes} min", style = MaterialTheme.typography.titleSmall)
                    Slider(
                        value = state.syncIntervalMinutes.toFloat(),
                        onValueChange = { viewModel.setSyncInterval(it.toInt()) },
                        valueRange = 15f..120f,
                        steps = 6
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Storage path", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(state.storagePath, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Delete old photos", style = MaterialTheme.typography.titleSmall)
                            Text("Delete files from Yandex Disk after retention period", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = state.deleteOldPhotos,
                            onCheckedChange = { viewModel.setDeleteOldPhotos(it) }
                        )
                    }

                    if (state.deleteOldPhotos) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Retention period: ${state.deleteAfterDays} days", style = MaterialTheme.typography.bodyMedium)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(onClick = {
                                if (state.deleteAfterDays > 1) viewModel.setDeleteAfterDays(state.deleteAfterDays - 1)
                            }) {
                                Text("−", style = MaterialTheme.typography.headlineMedium)
                            }
                            Text(
                                "${state.deleteAfterDays}",
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.width(40.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            IconButton(onClick = {
                                viewModel.setDeleteAfterDays(state.deleteAfterDays + 1)
                            }) {
                                Text("+", style = MaterialTheme.typography.headlineMedium)
                            }
                        }
                    }
                }
            }

            Text(
                "Get OAuth token at https://oauth.yandex.ru",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerField(value: Long, onValueChange: (Long) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    OutlinedButton(onClick = { showDialog = true }) {
        Text(formatDate(value))
    }

    if (showDialog) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = value.takeIf { it > 0 } ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { onValueChange(it) }
                    showDialog = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private fun formatDate(millis: Long): String {
    val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    return sdf.format(Date(millis))
}