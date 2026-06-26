package com.sharkord.android.ui.login

import com.sharkord.android.ui.theme.SharkordTheme
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Fingerprint
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
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = viewModel()
) {
    val context = LocalContext.current
    
    // synchronously check for a saved valid session on the very first frame
    // to prevent flashing of the login form/fields during auto-login transitions
    val hasSavedSession = remember(context) {
        SharkordClient.initialize(context)
        SharkordClient.session.hasValidSession()
    }
    
    // load saved URL on startup
    LaunchedEffect(Unit) {
        viewModel.initialize(context, onLoginSuccess)
    }
    
    val bgColor = SharkordTheme.colors.bgColor
    val cardColor = SharkordTheme.colors.cardColor
    val primaryText = SharkordTheme.colors.primaryText
    val foregroundText = SharkordTheme.colors.foregroundText
    val accentColor = SharkordTheme.colors.accentColor

    // render a premium full-screen splash screen immediately during auto-login transitions
    if (!viewModel.hideSplashScreen && (hasSavedSession || viewModel.isAutoLoggingIn)) {
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
                // show cached server logo if available in saved session
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

                if (!viewModel.showBiometricLaunchPrompt) {
                    CircularProgressIndicator(
                        color = accentColor,
                        modifier = Modifier.size(48.dp),
                        strokeWidth = 3.dp
                    )
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }
                
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
                    color = SharkordTheme.colors.primaryText.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (viewModel.showBiometricLaunchPrompt) {
            LaunchedEffect(Unit) {
                val activity = context as? FragmentActivity
                if (activity != null) {
                    val executor = ContextCompat.getMainExecutor(activity)
                    val biometricPrompt = BiometricPrompt(activity, executor,
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                super.onAuthenticationError(errorCode, errString)
                                viewModel.onBiometricLaunchCancel()
                            }

                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                super.onAuthenticationSucceeded(result)
                                viewModel.onBiometricLaunchSuccess()
                            }
                        }
                    )
                    val promptInfo = BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Sblocco Applicazione")
                        .setSubtitle("Conferma l'identità per aprire Sharkord")
                        .setNegativeButtonText("Annulla")
                        .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK)
                        .build()
                    biometricPrompt.authenticate(promptInfo)
                } else {
                    viewModel.onBiometricLaunchCancel()
                }
            }
        }
        
        return
    }

    if (viewModel.showBiometricSavePrompt) {
        AlertDialog(
            onDismissRequest = { viewModel.onBiometricSaveAnswer(false) },
            title = { Text("Abilita Impronta Digitale", color = foregroundText) },
            text = { Text("Vuoi accedere a Sharkord con l'impronta digitale la prossima volta?", color = primaryText) },
            confirmButton = {
                TextButton(onClick = { viewModel.onBiometricSaveAnswer(true) }) {
                    Text("Sì", color = accentColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onBiometricSaveAnswer(false) }) {
                    Text("No", color = SharkordTheme.colors.primaryText.copy(alpha = 0.6f))
                }
            },
            containerColor = cardColor
        )
    }

    if (viewModel.showBiometricLaunchPrompt) {
        LaunchedEffect(Unit) {
            val activity = context as? FragmentActivity
            if (activity != null) {
                val executor = ContextCompat.getMainExecutor(activity)
                val biometricPrompt = BiometricPrompt(activity, executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            super.onAuthenticationError(errorCode, errString)
                            viewModel.onBiometricLaunchCancel()
                        }

                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            super.onAuthenticationSucceeded(result)
                            viewModel.onBiometricLaunchSuccess()
                        }
                    }
                )
                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Sblocco Applicazione")
                    .setSubtitle("Conferma l'identità per aprire Sharkord")
                    .setNegativeButtonText("Annulla")
                    .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK)
                    .build()
                biometricPrompt.authenticate(promptInfo)
            } else {
                viewModel.onBiometricLaunchCancel()
            }
        }
    }

    if (viewModel.showInsecureConnectionPrompt) {
        AlertDialog(
            onDismissRequest = { viewModel.onInsecureConnectionCancel() },
            title = { Text(stringResource(R.string.connect_unencryptedConnection), color = foregroundText) },
            text = { Text(stringResource(R.string.connect_unencryptedConnectionPrompt), color = primaryText) },
            confirmButton = {
                TextButton(onClick = { viewModel.onInsecureConnectionConfirm(context) }) {
                    Text(stringResource(R.string.connect_connectBtn), color = accentColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onInsecureConnectionCancel() }) {
                    Text(stringResource(R.string.connect_cancel), color = SharkordTheme.colors.primaryText.copy(alpha = 0.6f))
                }
            },
            containerColor = cardColor
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .imePadding()
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
                .verticalScroll(rememberScrollState())
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

            // title (Server Name or default "Sharkord")
            Text(
                text = viewModel.serverName,
                color = foregroundText,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center
            )
            
            // server Description (if present)
            viewModel.serverDescription?.let { desc ->
                if (desc.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = desc,
                        color = SharkordTheme.colors.primaryText.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))

            // form Card
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
                            placeholder = { Text("https://demo.sharkord.com", color = SharkordTheme.colors.primaryText.copy(alpha = 0.5f)) },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = primaryText) },
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = foregroundText,
                                unfocusedTextColor = foregroundText,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedLabelColor = accentColor,
                                unfocusedLabelColor = SharkordTheme.colors.primaryText.copy(alpha = 0.6f),
                                focusedIndicatorColor = accentColor,
                                unfocusedIndicatorColor = SharkordTheme.colors.primaryText.copy(alpha = 0.6f).copy(alpha = 0.5f)
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // error Message
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

                        // next Button
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

                        // email / Identity Input
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
                                unfocusedLabelColor = SharkordTheme.colors.primaryText.copy(alpha = 0.6f),
                                focusedIndicatorColor = accentColor,
                                unfocusedIndicatorColor = SharkordTheme.colors.primaryText.copy(alpha = 0.6f).copy(alpha = 0.5f)
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // password Input
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
                                unfocusedLabelColor = SharkordTheme.colors.primaryText.copy(alpha = 0.6f),
                                focusedIndicatorColor = accentColor,
                                unfocusedIndicatorColor = SharkordTheme.colors.primaryText.copy(alpha = 0.6f).copy(alpha = 0.5f)
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // switch button to save login (Login automatically)
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
                                    uncheckedThumbColor = SharkordTheme.colors.primaryText.copy(alpha = 0.6f),
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

                        // error Message
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

                        // login Button Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { viewModel.onLoginClick(context, onLoginSuccess) },
                                enabled = !viewModel.isLoading,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = primaryText,
                                    contentColor = bgColor
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .weight(1f)
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

                            if (SharkordClient.session.hasBiometricCredentials() && viewModel.isBiometricSupported(context)) {
                                Spacer(modifier = Modifier.width(12.dp))
                                IconButton(
                                    onClick = {
                                        val activity = context as? FragmentActivity
                                        if (activity != null) {
                                            val executor = ContextCompat.getMainExecutor(activity)
                                            val biometricPrompt = BiometricPrompt(activity, executor,
                                                object : BiometricPrompt.AuthenticationCallback() {
                                                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                                        super.onAuthenticationError(errorCode, errString)
                                                        if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                                                            viewModel.errorMessage = errString.toString()
                                                        }
                                                    }

                                                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                                        super.onAuthenticationSucceeded(result)
                                                        val creds = SharkordClient.session.getBiometricCredentials()
                                                        if (creds != null) {
                                                            viewModel.identity = creds.first
                                                            viewModel.password = creds.second
                                                            viewModel.onLoginClick(context, onLoginSuccess, isBiometric = true)
                                                        }
                                                    }
                                                }
                                            )
                                            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                                                .setTitle("Accedi con Impronta")
                                                .setSubtitle("Usa l'impronta per accedere a Sharkord")
                                                .setNegativeButtonText("Annulla")
                                                .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK)
                                                .build()
                                            biometricPrompt.authenticate(promptInfo)
                                        }
                                    },
                                    modifier = Modifier
                                        .size(56.dp)
                                        .background(cardColor, RoundedCornerShape(16.dp))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Fingerprint,
                                        contentDescription = "Login with Fingerprint",
                                        tint = accentColor,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
