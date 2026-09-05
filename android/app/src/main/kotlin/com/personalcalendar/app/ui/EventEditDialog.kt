package com.personalcalendar.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.personalcalendar.app.data.CalendarEvent
import com.personalcalendar.app.data.EventCategory
import com.personalcalendar.app.data.Recurrence
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val REMINDER_OPTIONS = listOf(
    null to "알림 없음",
    0 to "정시",
    5 to "5분 전",
    10 to "10분 전",
    30 to "30분 전",
    60 to "1시간 전",
    1440 to "1일 전"
)

private val FREQ_OPTIONS = listOf(
    "none" to "반복 안 함",
    "daily" to "매일",
    "weekly" to "매주",
    "monthly" to "매월",
    "yearly" to "매년"
)

private val FREQ_UNIT = mapOf(
    "daily" to "일마다",
    "weekly" to "주마다",
    "monthly" to "개월마다",
    "yearly" to "년마다"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditDialog(
    editing: CalendarEvent?,
    prefillDate: LocalDate,
    prefillStart: String?,
    categories: List<EventCategory>,
    onDismiss: () -> Unit,
    onSave: (
        id: String?,
        title: String,
        date: String,
        start: String,
        end: String,
        categoryId: String,
        memo: String,
        recurrence: Recurrence,
        reminderMinutes: Int?
    ) -> Unit,
    onDelete: (String) -> Unit
) {
    var title by remember { mutableStateOf(editing?.title.orEmpty()) }
    var date by remember { mutableStateOf(editing?.date ?: prefillDate.toString()) }
    var start by remember { mutableStateOf(editing?.start ?: prefillStart.orEmpty()) }
    var end by remember { mutableStateOf(editing?.end.orEmpty()) }
    var categoryId by remember { mutableStateOf(editing?.categoryId ?: categories.firstOrNull()?.id.orEmpty()) }
    var memo by remember { mutableStateOf(editing?.memo.orEmpty()) }
    var freq by remember { mutableStateOf(editing?.recurrence?.freq ?: "none") }
    var interval by remember { mutableStateOf((editing?.recurrence?.interval ?: 1).toString()) }
    var until by remember { mutableStateOf(editing?.recurrence?.until.orEmpty()) }
    var reminderMinutes by remember { mutableStateOf(editing?.reminderMinutes) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showUntilPicker by remember { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var freqMenuExpanded by remember { mutableStateOf(false) }
    var reminderMenuExpanded by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    // Outside-tap dismiss is off on purpose: an accidental tap outside the form while
    // typing shouldn't discard an in-progress add/edit. 취소/back button still work.
    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false)
    ) {
        Column(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                if (editing != null) "일정 수정" else "새 일정",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(12.dp)

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("제목") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(10.dp)

            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text("날짜: $date")
            }
            Spacer(10.dp)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showStartPicker = true }, modifier = Modifier.weight(1f)) {
                    Text(if (start.isBlank()) "시작 시간" else "시작 $start")
                }
                OutlinedButton(onClick = { showEndPicker = true }, modifier = Modifier.weight(1f)) {
                    Text(if (end.isBlank()) "종료 시간" else "종료 $end")
                }
            }
            Spacer(10.dp)

            ExposedDropdownMenuBox(expanded = categoryMenuExpanded, onExpandedChange = { categoryMenuExpanded = it }) {
                OutlinedTextField(
                    value = categories.find { it.id == categoryId }?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("카테고리") },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
                    leadingIcon = {
                        val c = categories.find { it.id == categoryId }
                        if (c != null) {
                            Box(
                                Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(c.color))
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                DropdownMenu(
                    expanded = categoryMenuExpanded,
                    onDismissRequest = { categoryMenuExpanded = false }
                ) {
                    categories.forEach { c ->
                        DropdownMenuItem(
                            text = { Text(c.name) },
                            onClick = { categoryId = c.id; categoryMenuExpanded = false }
                        )
                    }
                }
            }
            Spacer(10.dp)

            ExposedDropdownMenuBox(expanded = freqMenuExpanded, onExpandedChange = { freqMenuExpanded = it }) {
                OutlinedTextField(
                    value = FREQ_OPTIONS.find { it.first == freq }?.second ?: "반복 안 함",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("반복") },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                DropdownMenu(
                    expanded = freqMenuExpanded,
                    onDismissRequest = { freqMenuExpanded = false }
                ) {
                    FREQ_OPTIONS.forEach { (value, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { freq = value; freqMenuExpanded = false })
                    }
                }
            }

            if (freq != "none") {
                Spacer(10.dp)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("매")
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = interval,
                        onValueChange = { v -> if (v.all { it.isDigit() }) interval = v },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(80.dp),
                        singleLine = true
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(FREQ_UNIT[freq] ?: "")
                }
                Spacer(6.dp)
                OutlinedButton(onClick = { showUntilPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (until.isBlank()) "반복 종료일 (선택 안 함)" else "반복 종료일: $until")
                }
                if (until.isNotBlank()) {
                    TextButton(onClick = { until = "" }) { Text("종료일 지우기") }
                }
                Text(
                    "반복 일정을 수정하거나 삭제하면 전체 반복 일정에 적용됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(10.dp)

            ExposedDropdownMenuBox(expanded = reminderMenuExpanded, onExpandedChange = { reminderMenuExpanded = it }) {
                OutlinedTextField(
                    value = REMINDER_OPTIONS.find { it.first == reminderMinutes }?.second ?: "알림 없음",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("알림") },
                    enabled = start.isNotBlank(),
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                DropdownMenu(
                    expanded = reminderMenuExpanded,
                    onDismissRequest = { reminderMenuExpanded = false }
                ) {
                    REMINDER_OPTIONS.forEach { (value, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { reminderMinutes = value; reminderMenuExpanded = false })
                    }
                }
            }
            if (start.isBlank()) {
                Text(
                    "알림을 설정하려면 시작 시간이 필요합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(10.dp)

            OutlinedTextField(
                value = memo,
                onValueChange = { memo = it },
                label = { Text("메모") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            errorText?.let {
                Spacer(6.dp)
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(16.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (editing != null) {
                    TextButton(onClick = { onDelete(editing.id) }) {
                        Text("삭제", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    Box {}
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss) { Text("취소") }
                    Button(onClick = {
                        if (title.isBlank()) {
                            errorText = "제목을 입력해주세요."
                            return@Button
                        }
                        if (start.isNotBlank() && end.isNotBlank() && end <= start) {
                            errorText = "종료 시간은 시작 시간보다 이후여야 합니다."
                            return@Button
                        }
                        if (reminderMinutes != null && start.isBlank()) {
                            errorText = "알림을 설정하려면 시작 시간을 입력해야 합니다."
                            return@Button
                        }
                        val recurrence = if (freq == "none") {
                            Recurrence("none")
                        } else {
                            Recurrence(freq, interval.toIntOrNull()?.coerceAtLeast(1) ?: 1, until.ifBlank { null })
                        }
                        onSave(editing?.id, title.trim(), date, start, end, categoryId, memo.trim(), recurrence, reminderMinutes)
                    }) {
                        Text("저장")
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.toLocalDateOrNull()?.toEpochMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = it.toLocalDateFromEpoch().toString() }
                    showDatePicker = false
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("취소") } }
        ) { DatePicker(state = state) }
    }

    if (showUntilPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = until.toLocalDateOrNull()?.toEpochMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showUntilPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { until = it.toLocalDateFromEpoch().toString() }
                    showUntilPicker = false
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { showUntilPicker = false }) { Text("취소") } }
        ) { DatePicker(state = state) }
    }

    if (showStartPicker) {
        TimePickerPopup(
            initial = start,
            onDismiss = { showStartPicker = false },
            onConfirm = { start = it; showStartPicker = false },
            onClear = { start = ""; showStartPicker = false }
        )
    }
    if (showEndPicker) {
        TimePickerPopup(
            initial = end,
            onDismiss = { showEndPicker = false },
            onConfirm = { end = it; showEndPicker = false },
            onClear = { end = ""; showEndPicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerPopup(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit, onClear: () -> Unit) {
    val parts = initial.split(":")
    val initHour = parts.getOrNull(0)?.toIntOrNull() ?: 9
    val initMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val state = rememberTimePickerState(initialHour = initHour, initialMinute = initMinute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm("%02d:%02d".format(state.hour, state.minute)) }) { Text("확인") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onClear) { Text("지우기") }
                TextButton(onClick = onDismiss) { Text("취소") }
            }
        },
        text = { TimePicker(state = state) }
    )
}

@Composable
private fun Spacer(height: androidx.compose.ui.unit.Dp) {
    androidx.compose.foundation.layout.Spacer(Modifier.height(height))
}

@Composable
private fun Spacer(modifier: Modifier) {
    androidx.compose.foundation.layout.Spacer(modifier)
}

private fun String.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()

private fun LocalDate.toEpochMillis(): Long =
    atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()

private fun Long.toLocalDateFromEpoch(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.of("UTC")).toLocalDate()
