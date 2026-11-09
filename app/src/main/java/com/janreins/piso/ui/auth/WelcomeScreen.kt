package com.janreins.piso.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.janreins.piso.data.local.isWeakPin
import com.janreins.piso.ui.components.PinDots
import com.janreins.piso.ui.components.PinKeypad
import com.janreins.piso.ui.components.PisoCard
import com.janreins.piso.ui.components.PisoPrimaryButton
import com.janreins.piso.ui.theme.IncomeGreen
import com.janreins.piso.ui.theme.TealContainer
import com.janreins.piso.ui.theme.TealPrimary
import com.janreins.piso.ui.theme.OnTealPrimary

@Composable
fun WelcomeScreen(
    onStartUsingPiso: (name: String, pin: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf("") }
    var enablePinLock by remember { mutableStateOf(false) }

    // PIN Setup states: 1 = Enter PIN, 2 = Confirm PIN, 3 = Confirmed (Keypad collapses)
    var pinStep by remember { mutableIntStateOf(1) }
    var initialPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }

    var showWeakPinWarning by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isPinError by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("welcome_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // App Icon & Name
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(TealContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    tint = TealPrimary,
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Piso",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp
                ),
                color = TealPrimary
            )

            Text(
                text = "Your private money book",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Local Profile Card
            PisoCard(
                contentPadding = 16.dp,
                cornerRadius = 20.dp
            ) {
                // Name Input Field at the Top
                Text(
                    text = "Welcome to Piso",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Everything is kept 100% offline on your device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMessage = null
                    },
                    label = { Text("Your Name (required)") },
                    placeholder = { Text("e.g. Maria") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = TealPrimary)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("welcome_name_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Optional 6-Digit PIN Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable {
                            enablePinLock = !enablePinLock
                            if (!enablePinLock) {
                                initialPin = ""
                                confirmPin = ""
                                pinStep = 1
                                errorMessage = null
                                isPinError = false
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = TealPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Set a 6-digit PIN (optional)",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            )
                            Text(
                                text = if (enablePinLock) "PIN lock active on app launch" else "App opens directly without lock",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = enablePinLock,
                        onCheckedChange = { checked ->
                            enablePinLock = checked
                            if (!checked) {
                                initialPin = ""
                                confirmPin = ""
                                pinStep = 1
                                errorMessage = null
                                isPinError = false
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = OnTealPrimary,
                            checkedTrackColor = TealPrimary
                        ),
                        modifier = Modifier.testTag("welcome_pin_switch")
                    )
                }

                // PIN Setup Section
                AnimatedVisibility(
                    visible = enablePinLock,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        when (pinStep) {
                            1 -> {
                                Text(
                                    text = "Enter a 6-digit PIN",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Step 1 of 2",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                PinDots(
                                    pinLength = initialPin.length,
                                    isError = isPinError,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )

                                PinKeypad(
                                    onDigitClick = { digit ->
                                        if (initialPin.length < 6) {
                                            isPinError = false
                                            errorMessage = null
                                            val newPin = initialPin + digit
                                            initialPin = newPin
                                            if (newPin.length == 6) {
                                                if (isWeakPin(newPin)) {
                                                    showWeakPinWarning = true
                                                } else {
                                                    pinStep = 2
                                                    confirmPin = ""
                                                }
                                            }
                                        }
                                    },
                                    onDeleteClick = {
                                        if (initialPin.isNotEmpty()) {
                                            initialPin = initialPin.dropLast(1)
                                            isPinError = false
                                            errorMessage = null
                                        }
                                    },
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }

                            2 -> {
                                Text(
                                    text = "Confirm 6-digit PIN",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Type it again to verify",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                PinDots(
                                    pinLength = confirmPin.length,
                                    isError = isPinError,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )

                                PinKeypad(
                                    onDigitClick = { digit ->
                                        if (confirmPin.length < 6) {
                                            isPinError = false
                                            errorMessage = null
                                            val newConfirm = confirmPin + digit
                                            confirmPin = newConfirm
                                            if (newConfirm.length == 6) {
                                                if (newConfirm == initialPin) {
                                                    // Step 3: Confirmed! Keypad will collapse
                                                    pinStep = 3
                                                    errorMessage = null
                                                } else {
                                                    isPinError = true
                                                    errorMessage = "PINs do not match."
                                                    confirmPin = ""
                                                }
                                            }
                                        }
                                    },
                                    onDeleteClick = {
                                        if (confirmPin.isNotEmpty()) {
                                            confirmPin = confirmPin.dropLast(1)
                                            isPinError = false
                                            errorMessage = null
                                        }
                                    },
                                    onClearClick = {
                                        initialPin = ""
                                        confirmPin = ""
                                        pinStep = 1
                                        errorMessage = null
                                        isPinError = false
                                    },
                                    clearButtonText = "Restart",
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }

                            3 -> {
                                // PIN Confirmed: Keypad collapses, showing compact green badge
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(TealContainer)
                                        .padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = IncomeGreen,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "6-digit PIN is set!",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    TextButton(
                                        onClick = {
                                            initialPin = ""
                                            confirmPin = ""
                                            pinStep = 1
                                        }
                                    ) {
                                        Text("Change PIN", fontSize = 13.sp, color = TealPrimary)
                                    }
                                }
                            }
                        }
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                PisoPrimaryButton(
                    text = "Start using Piso",
                    onClick = {
                        val cleanName = name.trim()
                        if (cleanName.isBlank()) {
                            errorMessage = "Please enter your name."
                            return@PisoPrimaryButton
                        }
                        if (enablePinLock) {
                            if (pinStep != 3 || initialPin.length != 6 || initialPin != confirmPin) {
                                errorMessage = "Please finish setting your 6-digit PIN or turn off PIN lock."
                                return@PisoPrimaryButton
                            }
                            onStartUsingPiso(cleanName, initialPin)
                        } else {
                            onStartUsingPiso(cleanName, null)
                        }
                    },
                    testTag = "welcome_start_button"
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Offline Assurance
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "All accounts and records stay strictly offline on this phone.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    // Weak PIN Dialog
    if (showWeakPinWarning) {
        AlertDialog(
            onDismissRequest = {
                showWeakPinWarning = false
                initialPin = ""
            },
            title = {
                Text(
                    text = "Weak PIN",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = "That PIN is easy to guess. Use it anyway?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showWeakPinWarning = false
                        pinStep = 2
                        confirmPin = ""
                    },
                    modifier = Modifier.testTag("weak_pin_yes_button")
                ) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showWeakPinWarning = false
                        initialPin = ""
                    },
                    modifier = Modifier.testTag("weak_pin_no_button")
                ) {
                    Text("Choose a different PIN")
                }
            }
        )
    }
}
