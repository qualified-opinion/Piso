package com.janreins.piso

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.janreins.piso.ui.settings.BackupTextDialog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsShareNoteTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun backupDialogExplainsPinOnReturnBeforeShareIsPressed() {
        var shareCalls = 0
        compose.setContent {
            MaterialTheme {
                BackupTextDialog(
                    jsonString = "{}",
                    onDismiss = {},
                    onCopy = {},
                    onShare = { shareCalls++ }
                )
            }
        }

        compose.onNodeWithText("Backup Text Ready").assertIsDisplayed()
        compose.onNodeWithText("Piso will ask for your PIN when you come back from sharing.").assertIsDisplayed()
        compose.onNodeWithText("Share").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, shareCalls) }

        compose.onNodeWithText("Share").performClick()
        compose.runOnIdle { assertEquals(1, shareCalls) }
    }
}
