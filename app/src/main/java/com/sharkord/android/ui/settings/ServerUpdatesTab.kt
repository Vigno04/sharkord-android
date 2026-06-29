package com.sharkord.android.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ServerUpdatesTab(
    viewModel: ServerSettingsViewModel,
    cardColor: Color,
    foregroundText: Color,
    primaryText: Color,
    accentColor: Color
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.fetchUpdateInfo()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "SERVER UPDATES",
            color = foregroundText,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        val info = uiState.updateInfo
        
        if (uiState.isLoading && info == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = accentColor)
            }
        } else if (info != null) {
            SettingsSection(title = stringResource(com.sharkord.android.R.string.settings_versionInfoGroup), cardColor = cardColor, foregroundText = foregroundText) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Current Version", color = primaryText)
                    Text(info.currentVersion ?: "Unknown", color = foregroundText, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Latest Version", color = primaryText)
                    Text(info.latestVersion ?: "Unknown", color = foregroundText, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (!info.canUpdate) {
                    Text("Updates are not supported on this installation.", color = Color(0xFFED4245), fontSize = 14.sp)
                } else if (info.hasUpdate) {
                    Text("A new update is available!", color = Color(0xFFFEE75C), fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.updateServer() },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(com.sharkord.android.R.string.settings_updateServerBtn))
                    }
                } else {
                    Text("Your server is up to date.", color = Color(0xFF4CAF50), fontSize = 14.sp)
                }
            }
        } else {
            Text("Failed to load update information.", color = Color(0xFFED4245))
        }
    }
}
