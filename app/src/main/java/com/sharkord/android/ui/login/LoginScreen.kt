package com.sharkord.android.ui.login

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sharkord.android.R
import com.sharkord.android.data.network.SharkordClient
import com.sharkord.android.ui.components.rememberAsyncImagePainter

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = viewModel()
) {
    val context = LocalContext.current
    
    // Synchronously check for a saved valid session on the very first frame
    // to prevent any flashing of the login form/fields during auto-login transitions!
    val hasSavedSession = remember(context) {
        SharkordClient.initialize(context)
        SharkordClient.session.hasValidSession()
    }
    
    // Load saved URL on startup
    LaunchedEffect(Unit) {
        viewModel.initialize(context, onLoginSuccess)
    }
    
    val bgColor = Color(0xFF1C1C1C)
    val cardColor = Color(0xFF2B2B2B)
    val primaryText = Color(0xFFE8E8E8)
    val foregroundText = Color(0xFFFAFAFA)
    val accentColor = Color(0xFFE8E8E8)

    // Render a premium full-screen splash screen immediately during auto-login transitions
    if (hasSavedSession || viewModel.isAutoLoggingIn) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Show cached server logo if available in saved session
                val savedLogoUrl = remember(context) {
                    if (SharkordClient.session.hasValidSession()) SharkordClient.session.serverLogoUrl else null
                }
                val serverLogoPainter = rememberAsyncImagePainter(savedLogoUrl)
                serverLogoPainter?.let {
                    Image(
                        painter = it,
                        contentDescription = "Server Logo",
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(32.dp))
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                CircularProgressIndicator(
                    color = accentColor,
                    modifier = Modifier.size(48.dp),
                    strokeWidth = 3.dp
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                val hostName = remember(context) {
                    SharkordClient.session.serverUrl
                        ?.removePrefix("https://")
                        ?.removePrefix("http://")
                        ?.substringBefore('/') 
                        ?: "Server"
                }
                Text(
                    text = "Connecting to $hostName...",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        if (viewModel.currentStep == LoginStep.CREDENTIALS) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { viewModel.changeServer() }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = stringResource(id = R.string.connect_changeServer),
                    tint = foregroundText,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = viewModel.serverUrl.removePrefix("https://").removePrefix("http://").substringBefore('/'),
                    color = foregroundText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val serverLogoPainter = rememberAsyncImagePainter(viewModel.serverLogoUrl)
            serverLogoPainter?.let {
                Image(
                    painter = it,
                    contentDescription = "Server Logo",
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(24.dp))
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Title (Server Name or default "Sharkord")
            Text(
                text = viewModel.serverName,
                color = foregroundText,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center
            )
            
            // Server Description (if present)
            viewModel.serverDescription?.let { desc ->
                if (desc.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = desc,
                        color = Color.Gray,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))

            // Form Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = cardColor
                ),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (viewModel.currentStep == LoginStep.URL) {
                            stringResource(id = R.string.connect_enterServerUrl)
                        } else {
                            stringResource(id = R.string.connect_connectBtn)
                        },
                        color = foregroundText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    if (viewModel.currentStep == LoginStep.URL) {

                        OutlinedTextField(
                            value = viewModel.serverUrl,
                            onValueChange = { viewModel.serverUrl = it },
                            label = { Text(stringResource(id = R.string.sidebar_server)) },
                            placeholder = { Text("https://demo.sharkord.com", color = Color.Gray.copy(alpha = 0.5f)) },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = primaryText) },
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = foregroundText,
                                unfocusedTextColor = foregroundText,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedLabelColor = accentColor,
                                unfocusedLabelColor = Color.Gray,
                                focusedIndicatorColor = accentColor,
                                unfocusedIndicatorColor = Color.Gray.copy(alpha = 0.5f)
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Error Message
                        viewModel.errorMessage?.let { error ->
                            Text(
                                text = error,
                                color = Color(0xFFEF4444),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Next Button
                        Button(
                            onClick = { viewModel.onNextClick(context) },
                            enabled = !viewModel.isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = primaryText,
                                contentColor = bgColor
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            if (viewModel.isLoading) {
                                CircularProgressIndicator(
                                    color = bgColor,
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Text(
                                    text = stringResource(id = R.string.connect_next),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                    } else {

                        // Email / Identity Input
                        OutlinedTextField(
                            value = viewModel.identity,
                            onValueChange = { viewModel.identity = it },
                            label = { Text(stringResource(id = R.string.connect_identityLabel)) },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = primaryText) },
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = foregroundText,
                                unfocusedTextColor = foregroundText,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedLabelColor = accentColor,
                                unfocusedLabelColor = Color.Gray,
                                focusedIndicatorColor = accentColor,
                                unfocusedIndicatorColor = Color.Gray.copy(alpha = 0.5f)
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Password Input
                        OutlinedTextField(
                            value = viewModel.password,
                            onValueChange = { viewModel.password = it },
                            label = { Text(stringResource(id = R.string.connect_passwordLabel)) },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = primaryText) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = foregroundText,
                                unfocusedTextColor = foregroundText,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedLabelColor = accentColor,
                                unfocusedLabelColor = Color.Gray,
                                focusedIndicatorColor = accentColor,
                                unfocusedIndicatorColor = Color.Gray.copy(alpha = 0.5f)
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Switch button to save login (Login automatically)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.autoLogin = !viewModel.autoLogin }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Switch(
                                checked = viewModel.autoLogin,
                                onCheckedChange = { viewModel.autoLogin = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = foregroundText,
                                    checkedTrackColor = accentColor.copy(alpha = 0.6f),
                                    uncheckedThumbColor = Color.Gray,
                                    uncheckedTrackColor = Color.Black.copy(alpha = 0.3f)
                                )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(id = R.string.connect_autoLoginLabel),
                                color = foregroundText,
                                fontSize = 14.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Error Message
                        viewModel.errorMessage?.let { error ->
                            Text(
                                text = error,
                                color = Color(0xFFEF4444),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Login Button
                        Button(
                            onClick = { viewModel.onLoginClick(context, onLoginSuccess) },
                            enabled = !viewModel.isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = primaryText,
                                contentColor = bgColor
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            if (viewModel.isLoading) {
                                CircularProgressIndicator(
                                    color = bgColor,
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Text(
                                    text = stringResource(id = R.string.connect_connectBtn),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
