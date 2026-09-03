package com.personalcalendar.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MONTH_FMT = DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREAN)
private val DAY_FMT = DateTimeFormatter.ofPattern("yyyy년 M월 d일", Locale.KOREAN)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: CalendarViewModel) {
    val state by viewModel.state.collectAsState()
    var showAccountDialog by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(periodLabel(state)) },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.navigate(-1) }) {
                            Icon(Icons.Filled.ChevronLeft, contentDescription = "이전")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.navigate(1) }) {
                            Icon(Icons.Filled.ChevronRight, contentDescription = "다음")
                        }
                        TextButton(onClick = { viewModel.today() }) { Text("오늘") }
                        IconButton(onClick = { showAccountDialog = true }) {
                            Icon(Icons.Filled.Person, contentDescription = "계정")
                        }
                        IconButton(onClick = {
                            viewModel.setTheme(if (state.theme == "dark") "light" else "dark")
                        }) {
                            Icon(
                                if (state.theme == "dark") Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                contentDescription = "테마 전환"
                            )
                        }
                    }
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    val modes = listOf(ViewMode.MONTH to "월", ViewMode.WEEK to "주", ViewMode.DAY to "일")
                    modes.forEachIndexed { index, (mode, label) ->
                        SegmentedButton(
                            selected = state.viewMode == mode,
                            onClick = { viewModel.setViewMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size)
                        ) { Text(label) }
                    }
                }
                CategoryFilterRow(
                    categories = state.categories,
                    activeIds = state.activeCategoryIds,
                    onToggle = { viewModel.toggleCategoryActive(it) },
                    onManageClick = { viewModel.openCategoryManager() }
                )
                state.availableUpdate?.let { release ->
                    UpdateBanner(
                        versionName = release.versionName,
                        downloading = state.updateDownloading,
                        onUpdateClick = { viewModel.startUpdateDownload() },
                        onDismiss = { viewModel.dismissUpdate() }
                    )
                }
                HorizontalDivider()
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.openNewEventDialog(state.selectedDate) }) {
                Icon(Icons.Filled.Add, contentDescription = "새 일정")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier
                    .weight(1.3f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                when (state.viewMode) {
                    ViewMode.MONTH -> {
                        WeekdayHeaderRow()
                        MonthGrid(
                            state = state,
                            onDayClick = { viewModel.selectDate(it) },
                            onDayLongClick = { viewModel.openNewEventDialog(it) }
                        )
                    }
                    ViewMode.WEEK -> {
                        val start = state.cursorDate.minusDays(state.cursorDate.dayOfWeek.value.toLong() % 7)
                        val days = (0 until 7).map { start.plusDays(it.toLong()) }
                        TimeGridView(
                            state = state,
                            days = days,
                            onSlotClick = { d, t -> viewModel.openNewEventDialog(d, t) },
                            onEventClick = { e, occDate -> viewModel.openEditEventDialog(e, occDate) },
                            onHeaderClick = { viewModel.goToDay(it) }
                        )
                    }
                    ViewMode.DAY -> {
                        TimeGridView(
                            state = state,
                            days = listOf(state.cursorDate),
                            onSlotClick = { d, t -> viewModel.openNewEventDialog(d, t) },
                            onEventClick = { e, occDate -> viewModel.openEditEventDialog(e, occDate) },
                            onHeaderClick = { }
                        )
                    }
                }
            }
            HorizontalDivider()
            EventListSection(
                selectedDate = state.selectedDate,
                occurrences = state.occurrencesOn(state.selectedDate),
                categoryColor = { id -> androidx.compose.ui.graphics.Color(state.categoryById(id).color) },
                onEventClick = { occ -> viewModel.openEditEventDialog(occ.event, occ.occurrenceDate) },
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }
    }

    if (state.isCreatingEvent) {
        EventEditDialog(
            editing = state.editingEvent,
            prefillDate = state.prefillDate ?: state.selectedDate,
            prefillStart = state.prefillStart,
            categories = state.categories,
            onDismiss = { viewModel.closeEventDialog() },
            onSave = { id, title, date, start, end, categoryId, memo, recurrence, reminderMinutes ->
                viewModel.saveEvent(id, title, date, start, end, categoryId, memo, recurrence, reminderMinutes)
            },
            onDelete = { id -> viewModel.deleteEvent(id) }
        )
    }

    if (state.showCategoryManager) {
        CategoryManagerDialog(
            categories = state.categories,
            onAdd = { name, color -> viewModel.addCategory(name, color) },
            onUpdate = { id, name, color -> viewModel.updateCategory(id, name, color) },
            onDelete = { id -> viewModel.deleteCategory(id) },
            onClose = { viewModel.closeCategoryManager() }
        )
    }

    if (showAccountDialog) {
        AccountDialog(
            authUser = state.authUser,
            authBusy = state.authBusy,
            authError = state.authError,
            hasPinSet = state.hasPinSet,
            onDismiss = { showAccountDialog = false },
            onLogin = { email, password -> viewModel.login(email, password) },
            onRegister = { email, password, name -> viewModel.register(email, password, name) },
            onLogout = { viewModel.logout() },
            onDiscordLogin = {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(viewModel.discordLoginUrl()))
                context.startActivity(intent)
            },
            onSetPin = { viewModel.setPin(it) },
            onClearPin = { viewModel.clearPin() }
        )
    }
}

@Composable
private fun UpdateBanner(
    versionName: String,
    downloading: Boolean,
    onUpdateClick: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(
                "새 버전 v$versionName 사용 가능",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.bodyMedium
            )
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                if (downloading) {
                    Text("다운로드 중…", color = MaterialTheme.colorScheme.onPrimaryContainer)
                } else {
                    TextButton(onClick = onUpdateClick) { Text("업데이트") }
                    TextButton(onClick = onDismiss) { Text("나중에") }
                }
            }
        }
    }
}

private fun periodLabel(state: CalendarUiState): String = when (state.viewMode) {
    ViewMode.MONTH -> state.cursorDate.format(MONTH_FMT)
    ViewMode.DAY -> state.cursorDate.format(DAY_FMT)
    ViewMode.WEEK -> {
        val start = state.cursorDate.minusDays(state.cursorDate.dayOfWeek.value.toLong() % 7)
        val end = start.plusDays(6)
        if (start.month == end.month) {
            "${start.year}년 ${start.monthValue}월 ${start.dayOfMonth}~${end.dayOfMonth}일"
        } else {
            "${start.monthValue}.${start.dayOfMonth} ~ ${end.monthValue}.${end.dayOfMonth}"
        }
    }
}
