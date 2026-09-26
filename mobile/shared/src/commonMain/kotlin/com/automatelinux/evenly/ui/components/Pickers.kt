package com.automatelinux.evenly.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.automatelinux.evenly.util.prettyDate
import com.automatelinux.evenly.util.toUtcMillis
import com.automatelinux.evenly.util.utcMillisToDate
import kotlinx.datetime.LocalDate

/** A pill that opens the Material3 date picker (never the platform one). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(date: LocalDate, tag: String, onChange: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.clip(RoundedCornerShape(50)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
            .clickable { open = true }.testTag(tag).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(6.dp))
        Text(prettyDate(date.toString()), style = MaterialTheme.typography.labelLarge)
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.toUtcMillis())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(utcMillisToDate(it)) }
                    open = false
                }, modifier = Modifier.testTag("$tag-ok")) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }, modifier = Modifier.testTag("$tag-cancel")) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

/** A small outlined pill that shows a value and opens something when tapped. */
@Composable
fun ValuePill(text: String, tag: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clip(RoundedCornerShape(50))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(50))
            .clickable(onClick = onClick).testTag(tag).padding(horizontal = 12.dp, vertical = 7.dp),
    )
}
