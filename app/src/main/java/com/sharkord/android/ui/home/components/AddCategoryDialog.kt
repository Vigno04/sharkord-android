package com.sharkord.android.ui.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun AddCategoryDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (name: String) -> Unit,
    bgColor: Color,
    cardColor: Color,
    primaryText: Color,
    foregroundText: Color
) {
    var categoryName by remember { mutableStateOf("") }

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
                    text = "Create Category",
                    color = foregroundText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(
                    text = "CATEGORY NAME",
                    color = primaryText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                OutlinedTextField(
                    value = categoryName,
                    onValueChange = { categoryName = it },
                    placeholder = { Text("new-category", color = primaryText.copy(alpha = 0.5f)) },
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
                            if (categoryName.isNotBlank()) {
                                onConfirm(categoryName.trim())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF5865F2),
                            disabledContainerColor = Color(0xFF5865F2).copy(alpha = 0.5f)
                        ),
                        enabled = categoryName.isNotBlank()
                    ) {
                        Text("Create", color = Color.White)
                    }
                }
            }
        }
    }
}
