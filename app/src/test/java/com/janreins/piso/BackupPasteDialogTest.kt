package com.janreins.piso

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.janreins.piso.data.models.BackupData
import com.janreins.piso.ui.settings.BackupImportState
import com.janreins.piso.ui.settings.BackupPasteDialog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupPasteDialogTest {
    @get:Rule val compose = createComposeRule()

    private var state by mutableStateOf(BackupImportState())
    private var visible by mutableStateOf(true)
    private var submittedText: String? = null

    private fun showDialog() {
        compose.setContent {
            MaterialTheme {
                if (visible) {
                    BackupPasteDialog(
                        backupImportState = state,
                        onReview = {
                            submittedText = it
                            state = BackupImportState(isLoading = true)
                        },
                        onDismiss = { visible = false }
                    )
                }
            }
        }
    }

    // Control completion explicitly so assertions never race a background dispatcher.
    // SettingsImportTest separately covers the ViewModel's stream-to-state mapping.
    private fun completeReview() {
        compose.runOnIdle {
            val data = BackupData.fromJsonString(requireNotNull(submittedText))
            state = if (data == null) {
                BackupImportState(error = "This is not a valid Piso backup.")
            } else {
                BackupImportState(data = data)
            }
        }
    }

    @Test
    fun invalidJsonKeepsDialogTextAndShowsErrorThenAllowsRetry() {
        showDialog()
        val input = compose.onNodeWithTag("import_json_input")
        val review = compose.onNodeWithTag("confirm_paste_import_button")
        input.performTextReplacement("not JSON")
        review.performClick()
        review.assertIsNotEnabled()
        input.assertTextEquals("not JSON")

        completeReview()

        compose.onNodeWithText("Paste Backup JSON").assertIsDisplayed()
        input.assertTextEquals("not JSON")
        compose.onNodeWithText("This is not a valid Piso backup.").assertIsDisplayed()
        review.assertIsEnabled()

        input.performTextReplacement("still not JSON")
        compose.onNodeWithText("This is not a valid Piso backup.").assertDoesNotExist()
        review.performClick()
        compose.runOnIdle { assertEquals("still not JSON", submittedText) }
        completeReview()
        input.assertTextEquals("still not JSON")
        compose.onNodeWithText("This is not a valid Piso backup.").assertIsDisplayed()
    }

    @Test
    fun validJsonClosesDialogOnlyAfterReviewCompletes() {
        val json = BackupData(
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
            exportedAt = 0L
        ).toJsonString()
        showDialog()
        compose.onNodeWithTag("import_json_input").performTextReplacement(json)
        compose.onNodeWithTag("confirm_paste_import_button").performClick()

        compose.onNodeWithText("Paste Backup JSON").assertIsDisplayed()
        compose.onNodeWithTag("import_json_input").assertTextEquals(json)
        compose.onNodeWithTag("confirm_paste_import_button").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(json, submittedText) }

        completeReview()

        compose.onNodeWithText("Paste Backup JSON").assertDoesNotExist()
        compose.onNodeWithTag("import_json_input").assertDoesNotExist()
    }
}
