package com.sharkord.android.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharkord.android.R
import com.sharkord.android.data.model.Role
import com.sharkord.android.data.model.Invite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerInvitesTab(
    invites: List<Invite>,
    roles: List<Role>,
    viewModel: ServerSettingsViewModel,
    cardColor: Color,
    foregroundText: Color,
    primaryText: Color,
    accentColor: Color
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboardManager = remember {
        context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    }

    if (showCreateDialog) {
        var maxUsesStr by remember { mutableStateOf("0") }
        var inviteCode by remember { mutableStateOf("") }
        var selectedRole by remember { mutableStateOf<Role?>(null) }
        
        var roleExpanded by remember { mutableStateOf(false) }
        var expirationExpanded by remember { mutableStateOf(false) }
        
        val expirations = listOf(R.string.settings_exp1Hour, R.string.settings_exp1Day, R.string.settings_exp7Days, R.string.settings_expNever)
        var selectedExpiration by remember { mutableStateOf(R.string.settings_expNever) }
        
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = cardColor,
            title = { Text(stringResource(R.string.settings_createInviteDialogTitle), color = foregroundText, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.settings_inviteCodeLabel), color = primaryText)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = inviteCode,
                        onValueChange = { inviteCode = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.settings_inviteCodePlaceholder), color = primaryText.copy(alpha = 0.5f)) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = foregroundText, unfocusedTextColor = primaryText, focusedIndicatorColor = accentColor
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(stringResource(R.string.settings_maxUsesLabel), color = primaryText)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = maxUsesStr,
                        onValueChange = { if (it.all { char -> char.isDigit() }) maxUsesStr = it },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = foregroundText, unfocusedTextColor = primaryText, focusedIndicatorColor = accentColor
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(stringResource(R.string.settings_assignedRoleLabel), color = primaryText)
                    Spacer(modifier = Modifier.height(4.dp))
                    ExposedDropdownMenuBox(
                        expanded = roleExpanded,
                        onExpandedChange = { roleExpanded = !roleExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedRole?.name ?: stringResource(R.string.common_none),
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = roleExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                focusedTextColor = foregroundText, unfocusedTextColor = primaryText, focusedIndicatorColor = accentColor
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = roleExpanded,
                            onDismissRequest = { roleExpanded = false },
                            modifier = Modifier.background(cardColor)
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.common_none), color = foregroundText) },
                                onClick = {
                                    selectedRole = null
                                    roleExpanded = false
                                }
                            )
                            roles.forEach { role ->
                                if (role.name.lowercase() != "everyone") {
                                    DropdownMenuItem(
                                        text = { Text(role.name, color = foregroundText) },
                                        onClick = {
                                            selectedRole = role
                                            roleExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(stringResource(R.string.settings_expirationLabel), color = primaryText)
                    Spacer(modifier = Modifier.height(4.dp))
                    ExposedDropdownMenuBox(
                        expanded = expirationExpanded,
                        onExpandedChange = { expirationExpanded = !expirationExpanded }
                    ) {
                        OutlinedTextField(
                            value = stringResource(selectedExpiration),
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expirationExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                focusedTextColor = foregroundText, unfocusedTextColor = primaryText, focusedIndicatorColor = accentColor
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = expirationExpanded,
                            onDismissRequest = { expirationExpanded = false },
                            modifier = Modifier.background(cardColor)
                        ) {
                            expirations.forEach { exp ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(exp), color = foregroundText) },
                                    onClick = {
                                        selectedExpiration = exp
                                        expirationExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val maxUses = maxUsesStr.toIntOrNull() ?: 0
                        val code = inviteCode.takeIf { it.isNotBlank() }
                        val roleId = selectedRole?.id
                        val expiresAt = when (selectedExpiration) {
                            R.string.settings_exp1Hour -> System.currentTimeMillis() + 3600_000L
                            R.string.settings_exp1Day -> System.currentTimeMillis() + 86400_000L
                            R.string.settings_exp7Days -> System.currentTimeMillis() + 7 * 86400_000L
                            else -> null
                        }
                        viewModel.createInvite(
                            maxUses = if (maxUses > 0) maxUses else null,
                            expiresAt = expiresAt,
                            roleId = roleId,
                            code = code
                        )
                        showCreateDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    Text(stringResource(R.string.settings_generateInviteBtn))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text(stringResource(R.string.common_cancel), color = primaryText)
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.settings_serverInvitesTitle), color = foregroundText, fontWeight = FontWeight.Bold)
            Button(
                onClick = { showCreateDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add")
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.settings_createInviteDialogTitle))
            }
        }

        if (invites.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.settings_noActiveInvites), color = primaryText)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(invites) { invite ->
                    InviteItemRow(
                        invite = invite,
                        onCopy = {
                            val clip = android.content.ClipData.newPlainText("Invite Code", invite.code)
                            clipboardManager.setPrimaryClip(clip)
                            android.widget.Toast.makeText(context, context.getString(R.string.settings_inviteCopied), android.widget.Toast.LENGTH_SHORT).show()
                        },
                        onDelete = { viewModel.deleteInvite(invite.id) },
                        cardColor = cardColor,
                        foregroundText = foregroundText,
                        primaryText = primaryText
                    )
                }
            }
        }
    }
}

@Composable
fun InviteItemRow(
    invite: Invite,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    cardColor: Color,
    foregroundText: Color,
    primaryText: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(cardColor)
            .clickable(onClick = onCopy)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(invite.code, color = foregroundText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            val usesText = if (invite.maxUses != null && invite.maxUses > 0) {
                stringResource(R.string.settings_inviteUses, invite.uses, invite.maxUses)
            } else {
                stringResource(R.string.settings_inviteUsesUnlimited, invite.uses)
            }
            Text(usesText, color = primaryText, fontSize = 12.sp)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFED4245))
        }
    }
}
