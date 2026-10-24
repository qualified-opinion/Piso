package com.janreins.piso.ui.auth

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.janreins.piso.data.local.UserProfileManager
import com.janreins.piso.ui.components.PinDots
import com.janreins.piso.ui.components.PinKeypad
import com.janreins.piso.ui.theme.ExpenseRed
import com.janreins.piso.ui.theme.TealContainer
import com.janreins.piso.ui.theme.TealPrimary
import kotlinx.coroutines.delay

@Composable
fun LockScreen(
    displayName: String,
    onUnlock: (pin: String) -> Boolean,
    onResetAllData: () -> Unit,
    getLockoutRemainingSeconds: () -> Int = { 0 },
    canResetAllData: () -> Boolean = { false },
    modifier: Modifier = Modifier
) {
    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isPinError by remember { mutableStateOf(false) }

    var lockoutSecondsRemaining by remember { mutableIntStateOf(getLockoutRemainingSeconds()) }

    var resetAvailable by remember { mutableStateOf(canResetAllData()) }
    var showResetDialog by remember { mutableStateOf(false) }
    var resetInputText by remember { mutableStateOf("") }
    var resetError by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()

    // Rate limiting countdown timer persisting across rebuilds/rotations
    LaunchedEffect(lockoutSecondsRemaining) {
        if (lockoutSecondsRemaining > 0) {
            delay(1000L)
            val currentRemaining = getLockoutRemainingSeconds()
            lockoutSecondsRemaining = currentRemaining
            if (currentRemaining == 0) {
                errorMessage = null
                isPinError = false
            }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("lock_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Lock Icon Badge
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(TealContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = TealPrimary,
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Title & Subtitle
            Text(
                text = "Welcome back, $displayName",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Enter your 6-digit PIN",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            // PIN Dots Indicator
            PinDots(
                pinLength = enteredPin.length,
                isError = isPinError,
                modifier = Modifier.testTag("lock_pin_dots")
            )

            // Error or Lockout Message
            if (lockoutSecondsRemaining > 0) {
                Text(
                    text = "Too many tries. Wait a moment.",
                    color = ExpenseRed,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            } else if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = ExpenseRed,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(26.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Big Number Pad (0-9 + delete key)
            PinKeypad(
                isEnabled = lockoutSecondsRemaining == 0,
                onDigitClick = { digit ->
                    if (lockoutSecondsRemaining == 0 && enteredPin.length < 6) {
                        isPinError = false
                        errorMessage = null
                        val newPin = enteredPin + digit
                        enteredPin = newPin

                        // Check PIN automatically when 6 digits are entered
                        if (newPin.length == 6) {
                            val success = onUnlock(newPin)
                            if (!success) {
                                val remainingLockout = getLockoutRemainingSeconds()
                                resetAvailable = canResetAllData()
                                isPinError = true
                                enteredPin = ""

                                if (remainingLockout > 0) {
                                    lockoutSecondsRemaining = remainingLockout
                                    errorMessage = "Too many tries. Wait a moment."
                                } else {
                                    errorMessage = "Wrong PIN. Try again."
                                }
                            } else {
                                isPinError = false
                                errorMessage = null
                            }
                        }
                    }
                },
                onDeleteClick = {
                    if (lockoutSecondsRemaining == 0 && enteredPin.isNotEmpty()) {
                        enteredPin = enteredPin.dropLast(1)
                        isPinError = false
                        errorMessage = null
                    }
                },
                modifier = Modifier.testTag("lock_pin_keypad")
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Offline Recovery Honest Message & Reset
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (resetAvailable) {
                        "Piso cannot recover a forgotten PIN because it is offline. You can keep using the app only after the correct PIN, or clear all data from this lock screen."
                    } else {
                        "Piso cannot recover a forgotten PIN because it is offline. After ${UserProfileManager.RESET_AFTER_FAILED_ATTEMPTS} wrong tries you can clear all data and start over."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    lineHeight = 18.sp,
                    modifier = Modifier.testTag("lock_forgot_pin_text")
                )

                // Only after enough wrong PINs, so picking up the phone isn't enough to wipe Piso
                if (resetAvailable) {
                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = {
                            resetInputText = ""
                            resetError = null
                            showResetDialog = true
                        },
                        modifier = Modifier.testTag("lock_forgot_reset_button")
                    ) {
                        Text(
                            text = "Clear all data and reset",
                            color = ExpenseRed,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Reset All Data Dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = ExpenseRed,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Reset Piso & Clear All Data?",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "This will permanently delete your offline profile and all money records on this device. You will start over fresh.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Type RESET in capital letters below to confirm:",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                    OutlinedTextField(
                        value = resetInputText,
                        onValueChange = {
                            resetInputText = it
                            resetError = null
                        },
                        placeholder = { Text("RESET") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reset_confirm_input"),
                        shape = RoundedCornerShape(10.dp)
                    )
                    if (resetError != null) {
                        Text(
                            text = resetError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (resetInputText.trim() == "RESET") {
                            showResetDialog = false
                            onResetAllData()
                        } else {
                            resetError = "Please type RESET exactly."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ExpenseRed
                    ),
                    modifier = Modifier.testTag("confirm_reset_button")
                ) {
                    Text("Clear All and Reset")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetDialog = false },
                    modifier = Modifier.testTag("cancel_reset_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
