package com.janreins.piso.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.janreins.piso.util.BackupCrypto

/** Why a new backup password can't be used, or null if it can. */
internal fun backupPasswordError(password: String, confirm: String): String? = when {
    password.length < BackupCrypto.MIN_PASSWORD_LENGTH ->
        "Use at least ${BackupCrypto.MIN_PASSWORD_LENGTH} characters."
    password != confirm -> "The two passwords don't match."
    else -> null
}

/**
 * Asked before a backup file is saved. Jan can protect it with a password, or save it as
 * before. [onSave] gets the password, or null to save without one.
 */
@Composable
fun ExportPasswordDialog(
    onDismiss: () -> Unit,
    onSave: (password: CharArray?) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Protect this backup?", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "With a password, nobody can read the file without it, even if it's copied off this phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Piso doesn't keep this password. If you forget it, the backup can't be opened.",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.error
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        error = null
                    },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("backup_password_input"),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = {
                        confirm = it
                        error = null
                    },
                    label = { Text("Type it again") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("backup_password_confirm_input"),
                    shape = RoundedCornerShape(12.dp)
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(
                    onClick = { onSave(null) },
                    modifier = Modifier.testTag("backup_save_without_password_button")
                ) {
                    Text("Save without a password")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val problem = backupPasswordError(password, confirm)
                    if (problem != null) {
                        error = problem
                    } else {
                        onSave(password.toCharArray())
                    }
                },
                modifier = Modifier.testTag("backup_save_with_password_button"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Save with password")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("backup_password_cancel_button")) {
                Text("Cancel")
            }
        }
    )
}

/** Shown when the backup being restored is password-protected. */
@Composable
fun UnlockBackupDialog(
    isLoading: Boolean,
    error: String?,
    onUnlock: (password: CharArray) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Password-protected backup", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Enter the password you chose when you saved this backup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    enabled = !isLoading,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("unlock_backup_password_input"),
                    shape = RoundedCornerShape(12.dp)
                )
                if (isLoading) CircularProgressIndicator()
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onUnlock(password.toCharArray()) },
                enabled = !isLoading && password.isNotEmpty(),
                modifier = Modifier.testTag("unlock_backup_button"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Unlock")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("unlock_backup_cancel_button")) {
                Text("Cancel")
            }
        }
    )
}
