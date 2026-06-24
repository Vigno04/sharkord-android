package com.sharkord.android.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.sharkord.android.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.JsonObject
import com.sharkord.android.data.model.PluginCommandArg
import com.sharkord.android.data.model.PluginCommandInfo
import com.sharkord.android.data.model.PluginLogEntry
import com.sharkord.android.data.model.PluginSettingDefinition
import com.sharkord.android.data.model.PluginSettingsResponse
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginLogsSheet(
    pluginId: String,
    logs: List<PluginLogEntry>,
    onDismiss: () -> Unit,
    cardColor: Color,
    foregroundText: Color,
    primaryText: Color,
    accentColor: Color
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = cardColor,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_pluginLogsTitle, pluginId),
                color = foregroundText,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (logs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.settings_noLogs), color = primaryText)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(logs) { log ->
                        val logColor = when (log.type) {
                            "error" -> Color.Red
                            "debug" -> Color.Gray
                            else -> accentColor
                        }
                        val icon = when (log.type) {
                            "error" -> Icons.Default.Error
                            "debug" -> Icons.Default.BugReport
                            else -> Icons.Default.Info
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.DarkGray.copy(alpha = 0.3f))
                                .padding(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = logColor,
                                modifier = Modifier.size(16.dp).padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = dateFormat.format(Date(log.timestamp)),
                                color = primaryText.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.width(64.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = log.message,
                                color = foregroundText,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginCommandsSheet(
    pluginId: String,
    commands: List<PluginCommandInfo>,
    viewModel: ServerSettingsViewModel,
    onDismiss: () -> Unit,
    cardColor: Color,
    foregroundText: Color,
    primaryText: Color,
    accentColor: Color
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = cardColor,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_pluginCommandsTitle, pluginId),
                color = foregroundText,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (commands.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.settings_noCommands), color = primaryText)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(commands) { cmd ->
                        CommandItemView(
                            command = cmd,
                            pluginId = pluginId,
                            viewModel = viewModel,
                            cardColor = cardColor,
                            foregroundText = foregroundText,
                            primaryText = primaryText,
                            accentColor = accentColor
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandItemView(
    command: PluginCommandInfo,
    pluginId: String,
    viewModel: ServerSettingsViewModel,
    cardColor: Color,
    foregroundText: Color,
    primaryText: Color,
    accentColor: Color
) {
    var expanded by remember { mutableStateOf(false) }
    val argValues = remember { mutableStateMapOf<String, String>() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.DarkGray.copy(alpha = 0.3f))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = command.name, color = foregroundText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (!command.description.isNullOrBlank()) {
                    Text(text = command.description, color = primaryText, fontSize = 14.sp)
                }
            }
            Button(
                onClick = {
                    if (command.args.isNullOrEmpty()) {
                        viewModel.executePluginCommand(pluginId, command.name, JsonObject())
                    } else {
                        expanded = !expanded
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) {
                Text(if (command.args.isNullOrEmpty() || expanded) stringResource(R.string.settings_executeCommand) else stringResource(R.string.settings_commandArgs))
            }
        }

        if (expanded && !command.args.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            command.args.forEach { arg ->
                OutlinedTextField(
                    value = argValues[arg.name] ?: "",
                    onValueChange = { argValues[arg.name] = it },
                    label = { Text("${arg.name} ${if (arg.required) "*" else ""}", color = primaryText) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = cardColor,
                        unfocusedContainerColor = cardColor,
                        focusedTextColor = foregroundText,
                        unfocusedTextColor = primaryText,
                        focusedIndicatorColor = accentColor
                    ),
                    keyboardOptions = if (arg.type == "number") KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default
                )
            }
            Button(
                onClick = {
                    val jsonArgs = JsonObject()
                    command.args.forEach { arg ->
                        val value = argValues[arg.name]
                        if (!value.isNullOrBlank()) {
                            when (arg.type) {
                                "number" -> jsonArgs.addProperty(arg.name, value.toDoubleOrNull() ?: 0.0)
                                "boolean" -> jsonArgs.addProperty(arg.name, value.toBooleanStrictOrNull() ?: false)
                                else -> jsonArgs.addProperty(arg.name, value)
                            }
                        }
                    }
                    viewModel.executePluginCommand(pluginId, command.name, jsonArgs)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) {
                Text(stringResource(R.string.settings_runCommand))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginSettingsSheet(
    pluginId: String,
    settingsResponse: PluginSettingsResponse,
    viewModel: ServerSettingsViewModel,
    onDismiss: () -> Unit,
    cardColor: Color,
    foregroundText: Color,
    primaryText: Color,
    accentColor: Color
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = cardColor,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_pluginSettingsTitle, pluginId),
                color = foregroundText,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (settingsResponse.definitions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.settings_noSettings), color = primaryText)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    settingsResponse.definitions.forEach { def ->
                        SettingItemView(
                            definition = def,
                            currentValue = settingsResponse.values[def.key],
                            pluginId = pluginId,
                            viewModel = viewModel,
                            cardColor = cardColor,
                            foregroundText = foregroundText,
                            primaryText = primaryText,
                            accentColor = accentColor
                        )
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
fun SettingItemView(
    definition: PluginSettingDefinition,
    currentValue: Any?,
    pluginId: String,
    viewModel: ServerSettingsViewModel,
    cardColor: Color,
    foregroundText: Color,
    primaryText: Color,
    accentColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.DarkGray.copy(alpha = 0.3f))
            .padding(16.dp)
    ) {
        Text(text = definition.name, color = foregroundText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        if (!definition.description.isNullOrBlank()) {
            Text(text = definition.description, color = primaryText, fontSize = 14.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))

        when (definition.type) {
            "boolean" -> {
                val isChecked = (currentValue as? Boolean) ?: (definition.defaultValue as? Boolean) ?: false
                Switch(
                    checked = isChecked,
                    onCheckedChange = { viewModel.updatePluginSetting(pluginId, definition.key, it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = accentColor, checkedTrackColor = accentColor.copy(alpha = 0.5f))
                )
            }
            "number" -> {
                var textValue by remember { mutableStateOf(currentValue?.toString() ?: definition.defaultValue?.toString() ?: "") }
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = cardColor,
                        unfocusedContainerColor = cardColor,
                        focusedTextColor = foregroundText,
                        unfocusedTextColor = primaryText,
                        focusedIndicatorColor = accentColor
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Button(
                    onClick = {
                        val num = textValue.toDoubleOrNull() ?: return@Button
                        viewModel.updatePluginSetting(pluginId, definition.key, num)
                    },
                    modifier = Modifier.padding(top = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    Text(stringResource(R.string.common_save))
                }
            }
            else -> { // string
                var textValue by remember { mutableStateOf(currentValue?.toString() ?: definition.defaultValue?.toString() ?: "") }
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = cardColor,
                        unfocusedContainerColor = cardColor,
                        focusedTextColor = foregroundText,
                        unfocusedTextColor = primaryText,
                        focusedIndicatorColor = accentColor
                    )
                )
                Button(
                    onClick = {
                        viewModel.updatePluginSetting(pluginId, definition.key, textValue)
                    },
                    modifier = Modifier.padding(top = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    Text(stringResource(R.string.common_save))
                }
            }
        }
    }
}
