package com.sharkord.android.ui.settings

import com.sharkord.android.ui.theme.SharkordTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DecimalFormat

private object StorageConstants {
    const val MEGABYTE = 1024L * 1024L
    const val GIGABYTE = 1024L * MEGABYTE
}

fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "Unlimited"
    val format = DecimalFormat("#.##")
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    val tb = gb / 1024.0
    return when {
        tb >= 1 -> "${format.format(tb)} TB"
        gb >= 1 -> "${format.format(gb)} GB"
        mb >= 1 -> "${format.format(mb)} MB"
        kb >= 1 -> "${format.format(kb)} KB"
        else -> "$bytes Bytes"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerStorageTab(
    viewModel: ServerSettingsViewModel
) {
    val colors = SharkordTheme.colors
    val cardColor = colors.cardColor
    val primaryText = colors.primaryText
    val foregroundText = colors.foregroundText
    val accentColor = colors.accentColor

    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val adminSettings = uiState.adminSettings
    val diskMetrics = uiState.diskMetrics

    if (adminSettings == null || diskMetrics == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = accentColor)
        }
        return
    }

    val isUploadsEnabled = adminSettings.storageUploadEnabled

    Column(modifier = Modifier.verticalScroll(scrollState).fillMaxSize()) {
        Text(
            text = "STORAGE SETTINGS",
            color = foregroundText,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        DiskMetricsCard(
            diskMetrics = diskMetrics,
            cardColor = cardColor,
            foregroundText = foregroundText,
            primaryText = primaryText,
            accentColor = accentColor
        )

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSection(title = "CONFIGURATION", cardColor = cardColor, foregroundText = foregroundText) {
            
            AdminSwitch(
                title = "Enable Uploads",
                description = "Allow users to upload files to the server.",
                checked = adminSettings.storageUploadEnabled,
                onCheckedChange = { viewModel.updateAdminSettings(adminSettings.copy(storageUploadEnabled = it)) },
                foregroundText = foregroundText, primaryText = primaryText, accentColor = accentColor
            )

            AdminSwitch(
                title = "File Sharing in DMs",
                description = "Allow users to share files in direct messages.",
                checked = adminSettings.storageFileSharingInDirectMessages,
                onCheckedChange = { viewModel.updateAdminSettings(adminSettings.copy(storageFileSharingInDirectMessages = it)) },
                foregroundText = if (isUploadsEnabled) foregroundText else primaryText.copy(alpha=0.5f), 
                primaryText = primaryText.copy(alpha=if (isUploadsEnabled) 1f else 0.5f), 
                accentColor = accentColor,
                enabled = isUploadsEnabled
            )

            Spacer(modifier = Modifier.height(16.dp))

            StorageSizeControl(
                label = "Storage Quota",
                description = "The maximum amount of storage space the entire server can use.",
                value = adminSettings.storageQuota ?: (25 * StorageConstants.GIGABYTE),
                minBytes = 1 * StorageConstants.GIGABYTE,
                maxBytes = diskMetrics.totalSpace,
                enabled = isUploadsEnabled,
                onValueChange = { viewModel.updateAdminSettings(adminSettings.copy(storageQuota = it)) },
                foregroundText = foregroundText, primaryText = primaryText, accentColor = accentColor,
                unitDivider = StorageConstants.GIGABYTE, unitName = "GB"
            )

            StorageSizeControl(
                label = "Max File Size",
                description = "The maximum size of a single file upload.",
                value = adminSettings.storageUploadMaxFileSize ?: (100 * StorageConstants.MEGABYTE),
                minBytes = 1 * StorageConstants.MEGABYTE,
                maxBytes = 10 * StorageConstants.GIGABYTE,
                enabled = isUploadsEnabled,
                onValueChange = { viewModel.updateAdminSettings(adminSettings.copy(storageUploadMaxFileSize = it)) },
                foregroundText = foregroundText, primaryText = primaryText, accentColor = accentColor
            )

            StorageSizeControl(
                label = "Max Avatar Size",
                description = "The maximum size for user avatars.",
                value = adminSettings.storageMaxAvatarSize ?: (3 * StorageConstants.MEGABYTE),
                minBytes = 1 * StorageConstants.MEGABYTE,
                maxBytes = 50 * StorageConstants.MEGABYTE,
                enabled = isUploadsEnabled,
                onValueChange = { viewModel.updateAdminSettings(adminSettings.copy(storageMaxAvatarSize = it)) },
                foregroundText = foregroundText, primaryText = primaryText, accentColor = accentColor
            )

            StorageSizeControl(
                label = "Max Banner Size",
                description = "The maximum size for user banners.",
                value = adminSettings.storageMaxBannerSize ?: (10 * StorageConstants.MEGABYTE),
                minBytes = 1 * StorageConstants.MEGABYTE,
                maxBytes = 50 * StorageConstants.MEGABYTE,
                enabled = isUploadsEnabled,
                onValueChange = { viewModel.updateAdminSettings(adminSettings.copy(storageMaxBannerSize = it)) },
                foregroundText = foregroundText, primaryText = primaryText, accentColor = accentColor
            )

            StorageSizeControl(
                label = "Quota Per User",
                description = "The maximum amount of storage space an individual user can use.",
                value = adminSettings.storageSpaceQuotaByUser ?: 0L,
                minBytes = 0L,
                maxBytes = diskMetrics.totalSpace,
                enabled = isUploadsEnabled,
                onValueChange = { viewModel.updateAdminSettings(adminSettings.copy(storageSpaceQuotaByUser = it)) },
                foregroundText = foregroundText, primaryText = primaryText, accentColor = accentColor,
                unitDivider = StorageConstants.GIGABYTE, unitName = "GB"
            )

            NumberWithPresetsControl(
                label = "Max Files Per Message",
                description = "The maximum number of files that can be attached to a single message.",
                value = adminSettings.storageMaxFilesPerMessage.toLong(),
                min = 1L,
                max = 100L,
                unit = "files",
                enabled = isUploadsEnabled,
                onValueChange = { viewModel.updateAdminSettings(adminSettings.copy(storageMaxFilesPerMessage = it.toInt())) },
                foregroundText = foregroundText, primaryText = primaryText, accentColor = accentColor
            )

            Spacer(modifier = Modifier.height(16.dp))

            DropdownSetting(
                label = "Overflow Action",
                description = "What happens when the storage quota is reached.",
                value = adminSettings.storageOverflowAction ?: "PREVENT_UPLOADS",
                options = listOf("PREVENT_UPLOADS" to "Prevent Uploads", "DELETE_OLD_FILES" to "Delete Old Files"),
                enabled = isUploadsEnabled,
                onValueChange = { viewModel.updateAdminSettings(adminSettings.copy(storageOverflowAction = it)) },
                foregroundText = foregroundText, primaryText = primaryText, accentColor = accentColor, cardColor = cardColor
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = primaryText.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(16.dp))

            AdminSwitch(
                title = "Image Optimization",
                description = "Enable server-side image compression and optimization.",
                checked = adminSettings.storageImageOptimizationEnabled,
                onCheckedChange = { viewModel.updateAdminSettings(adminSettings.copy(storageImageOptimizationEnabled = it)) },
                foregroundText = if (isUploadsEnabled) foregroundText else primaryText.copy(alpha=0.5f), 
                primaryText = primaryText.copy(alpha=if (isUploadsEnabled) 1f else 0.5f), 
                accentColor = accentColor,
                enabled = isUploadsEnabled
            )

            if (adminSettings.storageImageOptimizationEnabled) {
                SliderControl(
                    label = "Image Quality",
                    description = "The quality of the optimized image.",
                    value = adminSettings.storageImageOptimizationQuality.toLong(),
                    min = 1L,
                    max = 100L,
                    unit = "%",
                    enabled = isUploadsEnabled,
                    onValueChange = { viewModel.updateAdminSettings(adminSettings.copy(storageImageOptimizationQuality = it.toInt())) },
                    foregroundText = foregroundText, primaryText = primaryText, accentColor = accentColor
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = primaryText.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(16.dp))

            AdminSwitch(
                title = "Signed URLs",
                description = "Enable temporary signed URLs for file access.",
                checked = adminSettings.storageSignedUrlsEnabled,
                onCheckedChange = { viewModel.updateAdminSettings(adminSettings.copy(storageSignedUrlsEnabled = it)) },
                foregroundText = foregroundText, primaryText = primaryText, accentColor = accentColor
            )

            if (adminSettings.storageSignedUrlsEnabled) {
                NumberWithPresetsControl(
                    label = "Signed URL TTL",
                    description = "How long a signed URL is valid for.",
                    value = adminSettings.storageSignedUrlsTtlSeconds.toLong() / 60L,
                    min = 1L,
                    max = 7 * 24 * 60L, // 1 week in minutes
                    unit = "mins",
                    enabled = true,
                    onValueChange = { viewModel.updateAdminSettings(adminSettings.copy(storageSignedUrlsTtlSeconds = (it * 60L).toInt())) },
                    foregroundText = foregroundText, primaryText = primaryText, accentColor = accentColor
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { viewModel.saveGeneralSettings() },
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                modifier = Modifier.align(Alignment.End),
                enabled = !uiState.isLoading
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = SharkordTheme.colors.foregroundText)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Save Changes")
            }
        }
    }
}

@Composable
fun DiskMetricsCard(
    diskMetrics: com.sharkord.android.data.model.DiskMetrics,
    cardColor: Color,
    foregroundText: Color,
    primaryText: Color,
    accentColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(cardColor)
            .padding(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Total Space", color = primaryText, fontSize = 14.sp)
                Text(formatSize(diskMetrics.totalSpace), color = foregroundText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Available Space", color = primaryText, fontSize = 14.sp)
                Text(formatSize(diskMetrics.freeSpace), color = foregroundText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text("System Used", color = primaryText, fontSize = 14.sp)
                Text(formatSize(diskMetrics.usedSpace), color = foregroundText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Sharkord Used", color = primaryText, fontSize = 14.sp)
                Text(formatSize(diskMetrics.sharkordUsedSpace), color = foregroundText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Disk Usage", color = primaryText, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))
        val usagePercent = if (diskMetrics.totalSpace > 0) {
            (diskMetrics.usedSpace.toFloat() / diskMetrics.totalSpace.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        LinearProgressIndicator(
            progress = { usagePercent },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = accentColor,
            trackColor = SharkordTheme.colors.cardColor
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${java.text.DecimalFormat("#.#").format(usagePercent * 100)}% used",
            color = primaryText,
            fontSize = 12.sp
        )
    }
}

@Composable
fun AdminSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    foregroundText: Color,
    primaryText: Color,
    accentColor: Color,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = foregroundText, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(description, color = primaryText, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = accentColor, 
                checkedTrackColor = accentColor.copy(alpha = 0.5f),
                disabledCheckedThumbColor = accentColor.copy(alpha = 0.5f),
                disabledCheckedTrackColor = accentColor.copy(alpha = 0.2f),
                disabledUncheckedThumbColor = SharkordTheme.colors.primaryText.copy(alpha = 0.6f),
                disabledUncheckedTrackColor = SharkordTheme.colors.cardColor
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageSizeControl(
    label: String,
    description: String,
    value: Long,
    minBytes: Long,
    maxBytes: Long,
    enabled: Boolean,
    onValueChange: (Long) -> Unit,
    foregroundText: Color,
    primaryText: Color,
    accentColor: Color,
    unitDivider: Long = StorageConstants.MEGABYTE,
    unitName: String = "MB"
) {
    val alpha = if (enabled) 1f else 0.5f
    val fText = foregroundText.copy(alpha = alpha)
    val pText = primaryText.copy(alpha = alpha)

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(label, color = fText, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(description, color = pText, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(8.dp))

        // slider logic using unitDivider
        val safeMaxBytes = maxOf(minBytes + unitDivider, maxBytes)
        val minUnit = (minBytes / unitDivider).toFloat()
        val maxUnit = (safeMaxBytes / unitDivider).toFloat()
        val safeMaxUnit = if (maxUnit > minUnit) maxUnit else minUnit + 1f
        val currentUnit = (value / unitDivider).toFloat().coerceIn(minUnit, safeMaxUnit)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = currentUnit,
                onValueChange = { onValueChange((it.toLong() * unitDivider)) },
                valueRange = minUnit..safeMaxUnit,
                enabled = enabled,
                colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(formatSize(value), color = fText, fontSize = 14.sp, modifier = Modifier.width(80.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = if (value == 0L) "" else (value / unitDivider).toString(),
                onValueChange = { 
                    val next = it.toLongOrNull() ?: 0L
                    onValueChange((next * unitDivider).coerceIn(minBytes, maxBytes))
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(130.dp).height(50.dp),
                enabled = enabled,
                suffix = { Text(unitName, color = pText, fontSize = 12.sp) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = fText,
                    unfocusedTextColor = pText,
                    focusedIndicatorColor = accentColor,
                    disabledContainerColor = Color.Transparent,
                    disabledTextColor = fText,
                    disabledIndicatorColor = SharkordTheme.colors.cardColor
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumberWithPresetsControl(
    label: String,
    description: String,
    value: Long,
    min: Long,
    max: Long,
    unit: String,
    enabled: Boolean,
    onValueChange: (Long) -> Unit,
    foregroundText: Color,
    primaryText: Color,
    accentColor: Color
) {
    val alpha = if (enabled) 1f else 0.5f
    val fText = foregroundText.copy(alpha = alpha)
    val pText = primaryText.copy(alpha = alpha)

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(label, color = fText, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(description, color = pText, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = value.toString(),
                onValueChange = { 
                    val next = it.toLongOrNull() ?: min
                    onValueChange(next.coerceIn(min, max))
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(130.dp).height(50.dp),
                enabled = enabled,
                suffix = { Text(unit, color = pText, fontSize = 12.sp) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = fText,
                    unfocusedTextColor = pText,
                    focusedIndicatorColor = accentColor,
                    disabledContainerColor = Color.Transparent,
                    disabledTextColor = fText,
                    disabledIndicatorColor = SharkordTheme.colors.cardColor
                )
            )
        }
    }
}

@Composable
fun SliderControl(
    label: String,
    description: String,
    value: Long,
    min: Long,
    max: Long,
    unit: String,
    enabled: Boolean,
    onValueChange: (Long) -> Unit,
    foregroundText: Color,
    primaryText: Color,
    accentColor: Color
) {
    val alpha = if (enabled) 1f else 0.5f
    val fText = foregroundText.copy(alpha = alpha)
    val pText = primaryText.copy(alpha = alpha)

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(label, color = fText, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(description, color = pText, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(it.toLong()) },
                valueRange = min.toFloat()..max.toFloat(),
                enabled = enabled,
                colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text("$value$unit", color = fText, fontSize = 14.sp, modifier = Modifier.width(40.dp))
        }
    }
}

@Composable
fun DropdownSetting(
    label: String,
    description: String,
    value: String,
    options: List<Pair<String, String>>,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    foregroundText: Color,
    primaryText: Color,
    accentColor: Color,
    cardColor: Color
) {
    val alpha = if (enabled) 1f else 0.5f
    val fText = foregroundText.copy(alpha = alpha)
    val pText = primaryText.copy(alpha = alpha)
    
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(label, color = fText, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(description, color = pText, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .border(1.dp, if (enabled) primaryText else SharkordTheme.colors.dividerColor, RoundedCornerShape(4.dp))
                .clickable(enabled = enabled) { expanded = true }
                .padding(16.dp)
        ) {
            Text(options.find { it.first == value }?.second ?: value, color = fText)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(cardColor)
        ) {
            options.forEach { (optionValue, optionLabel) ->
                DropdownMenuItem(
                    text = { Text(optionLabel, color = foregroundText) },
                    onClick = {
                        onValueChange(optionValue)
                        expanded = false
                    }
                )
            }
        }
    }
}
