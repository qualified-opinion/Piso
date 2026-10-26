package com.janreins.piso.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.janreins.piso.util.DateUtil

/**
 * A "Date: …" button that opens a date picker. The picked day keeps the time of day of
 * [dateMillis], so same-day entries keep a natural order.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    dateMillis: Long,
    onDateChange: (Long) -> Unit,
    testTagPrefix: String,
    modifier: Modifier = Modifier,
    label: String = "Date"
) {
    var showDatePicker by remember { mutableStateOf(false) }

    OutlinedButton(
        onClick = { showDatePicker = true },
        modifier = modifier
            .fillMaxWidth()
            .testTag("${testTagPrefix}_date_button"),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CalendarMonth,
            contentDescription = null,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text("$label: ${DateUtil.formatDate(dateMillis)}")
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = DateUtil.toPickerUtcMillis(dateMillis)
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { picked ->
                            onDateChange(DateUtil.fromPickerUtcMillis(picked, keepTimeOf = dateMillis))
                        }
                        showDatePicker = false
                    },
                    modifier = Modifier.testTag("${testTagPrefix}_date_confirm")
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
