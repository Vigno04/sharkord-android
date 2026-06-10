package com.sharkord.android.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sharkord.android.data.model.ChannelType

@Composable
fun AddChannelDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (name: String, type: ChannelType) -> Unit,
    bgColor: Color,
    cardColor: Color,
    primaryText: Color,
    foregroundText: Color
) {
    var channelName by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(ChannelType.TEXT) }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = bgColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Create Channel",
                    color = foregroundText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(
                    text = "CHANNEL TYPE",
                    color = primaryText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    TypeOption(
                        text = "Text",
                        isSelected = selectedType == ChannelType.TEXT,
                        onClick = { selectedType = ChannelType.TEXT },
                        cardColor = cardColor,
                        foregroundText = foregroundText,
                        modifier = Modifier.weight(1f)
                    )
                    TypeOption(
                        text = "Voice",
                        isSelected = selectedType == ChannelType.VOICE,
                        onClick = { selectedType = ChannelType.VOICE },
                        cardColor = cardColor,
                        foregroundText = foregroundText,
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    text = "CHANNEL NAME",
                    color = primaryText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                OutlinedTextField(
                    value = channelName,
                    onValueChange = { channelName = it },
                    placeholder = { Text("new-channel", color = primaryText.copy(alpha = 0.5f)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = foregroundText,
                        unfocusedTextColor = foregroundText,
                        focusedBorderColor = Color(0xFF5865F2),
                        unfocusedBorderColor = cardColor,
                        focusedContainerColor = cardColor,
                        unfocusedContainerColor = cardColor
                    ),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Cancel", color = primaryText)
                    }
                    Button(
                        onClick = {
                            if (channelName.isNotBlank()) {
                                onConfirm(channelName.trim(), selectedType)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF5865F2),
                            disabledContainerColor = Color(0xFF5865F2).copy(alpha = 0.5f)
                        ),
                        enabled = channelName.isNotBlank()
                    ) {
                        Text("Create", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeOption(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    cardColor: Color,
    foregroundText: Color,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) Color(0xFF5865F2).copy(alpha = 0.2f) else cardColor

    Box(
        modifier = modifier
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = foregroundText,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
