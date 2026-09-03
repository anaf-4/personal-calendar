package com.personalcalendar.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personalcalendar.app.data.CalendarEvent
import com.personalcalendar.app.data.EventCategory
import com.personalcalendar.app.data.EventOccurrence
import com.personalcalendar.app.data.Recurrence
import com.personalcalendar.app.data.Repository
import com.personalcalendar.app.BuildConfig
import com.personalcalendar.app.data.SettingsStore
import com.personalcalendar.app.data.expandOccurrences
import com.personalcalendar.app.reminders.ReminderScheduler
import com.personalcalendar.app.update.GithubRelease
import com.personalcalendar.app.update.UpdateChecker
import com.personalcalendar.app.update.UpdateInstaller
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ViewMode { MONTH, WEEK, DAY }

data class CalendarUiState(
    val loading: Boolean = true,
    val events: List<CalendarEvent> = emptyList(),
    val categories: List<EventCategory> = emptyList(),
    val activeCategoryIds: Set<String> = emptySet(),
    val viewMode: ViewMode = ViewMode.MONTH,
    val cursorDate: LocalDate = LocalDate.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val theme: String = "dark",
    val editingEvent: CalendarEvent? = null,
    val isCreatingEvent: Boolean = false,
    val prefillDate: LocalDate? = null,
    val prefillStart: String? = null,
    val showCategoryManager: Boolean = false,
    val availableUpdate: GithubRelease? = null,
    val updateDownloading: Boolean = false
) {
    fun categoryById(id: String): EventCategory =
        categories.find { it.id == id } ?: EventCategory("unknown", "기타", 0xFF9A9CA6)

    fun occurrencesInRange(start: LocalDate, end: LocalDate): List<EventOccurrence> =
        events.filter { activeCategoryIds.contains(it.categoryId) }
            .flatMap { expandOccurrences(it, start, end) }

    fun occurrencesOn(date: LocalDate): List<EventOccurrence> = occurrencesInRange(date, date)
}

class CalendarViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = Repository(application)
    private val settingsStore = SettingsStore(application)

    private val _state = MutableStateFlow(CalendarUiState())
    val state: StateFlow<CalendarUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val events = repository.loadEvents()
            val categories = repository.loadCategories()
            _state.update {
                it.copy(
                    loading = false,
                    events = events,
                    categories = categories,
                    activeCategoryIds = categories.map { c -> c.id }.toSet()
                )
            }
            events.forEach { ReminderScheduler.scheduleForEvent(getApplication(), it) }
            launch {
                val release = UpdateChecker.checkLatest(BuildConfig.VERSION_NAME)
                if (release?.apkAsset != null) {
                    _state.update { it.copy(availableUpdate = release) }
                }
            }
            settingsStore.theme.collect { t -> _state.update { s -> s.copy(theme = t) } }
        }
    }

    fun dismissUpdate() {
        _state.update { it.copy(availableUpdate = null) }
    }

    fun startUpdateDownload() {
        val release = _state.value.availableUpdate ?: return
        val asset = release.apkAsset ?: return
        _state.update { it.copy(updateDownloading = true) }
        UpdateInstaller.downloadAndInstall(getApplication(), asset.browserDownloadUrl) {
            _state.update { it.copy(updateDownloading = false, availableUpdate = null) }
        }
    }

    fun setTheme(value: String) {
        _state.update { it.copy(theme = value) }
        viewModelScope.launch { settingsStore.setTheme(value) }
    }

    fun setViewMode(mode: ViewMode) {
        _state.update { it.copy(viewMode = mode) }
    }

    fun goToDay(date: LocalDate) {
        _state.update { it.copy(cursorDate = date, selectedDate = date, viewMode = ViewMode.DAY) }
    }

    fun selectDate(date: LocalDate, moveCursor: Boolean = true) {
        _state.update {
            it.copy(selectedDate = date, cursorDate = if (moveCursor) date else it.cursorDate)
        }
    }

    fun navigate(direction: Int) {
        _state.update { s ->
            val newCursor = when (s.viewMode) {
                ViewMode.MONTH -> s.cursorDate.withDayOfMonth(1).plusMonths(direction.toLong())
                ViewMode.WEEK -> s.cursorDate.plusWeeks(direction.toLong())
                ViewMode.DAY -> s.cursorDate.plusDays(direction.toLong())
            }
            s.copy(cursorDate = newCursor)
        }
    }

    fun today() {
        val now = LocalDate.now()
        _state.update { it.copy(cursorDate = now, selectedDate = now) }
    }

    fun toggleCategoryActive(categoryId: String) {
        _state.update { s ->
            val active = s.activeCategoryIds.toMutableSet()
            if (!active.add(categoryId)) active.remove(categoryId)
            s.copy(activeCategoryIds = active)
        }
    }

    fun openNewEventDialog(date: LocalDate, start: String? = null) {
        _state.update {
            it.copy(isCreatingEvent = true, editingEvent = null, prefillDate = date, prefillStart = start)
        }
    }

    fun openEditEventDialog(event: CalendarEvent, occurrenceDate: String) {
        _state.update {
            it.copy(
                isCreatingEvent = true,
                editingEvent = event.copy(date = occurrenceDate),
                prefillDate = null,
                prefillStart = null
            )
        }
    }

    fun closeEventDialog() {
        _state.update { it.copy(isCreatingEvent = false, editingEvent = null, prefillDate = null, prefillStart = null) }
    }

    fun saveEvent(
        id: String?,
        title: String,
        date: String,
        start: String,
        end: String,
        categoryId: String,
        memo: String,
        recurrence: Recurrence,
        reminderMinutes: Int?
    ) {
        val eventId = id ?: UUID.randomUUID().toString()
        val newEvent = CalendarEvent(eventId, title, date, start, end, categoryId, memo, recurrence, reminderMinutes)
        _state.update { s ->
            val updated = if (id != null) {
                s.events.map { if (it.id == id) newEvent else it }
            } else {
                s.events + newEvent
            }
            s.copy(
                events = updated,
                selectedDate = LocalDate.parse(date),
                cursorDate = LocalDate.parse(date),
                isCreatingEvent = false,
                editingEvent = null
            )
        }
        persistEvents()
        ReminderScheduler.scheduleForEvent(getApplication(), newEvent)
    }

    fun deleteEvent(id: String) {
        _state.update { s -> s.copy(events = s.events.filter { it.id != id }, isCreatingEvent = false, editingEvent = null) }
        persistEvents()
        ReminderScheduler.cancelForEvent(getApplication(), id)
    }

    private fun persistEvents() {
        val snapshot = _state.value.events
        viewModelScope.launch { repository.saveEvents(snapshot) }
    }

    private fun persistCategories() {
        val snapshot = _state.value.categories
        viewModelScope.launch { repository.saveCategories(snapshot) }
    }

    fun openCategoryManager() {
        _state.update { it.copy(showCategoryManager = true) }
    }

    fun closeCategoryManager() {
        _state.update { it.copy(showCategoryManager = false) }
    }

    fun addCategory(name: String, color: Long) {
        val id = "cat_" + UUID.randomUUID().toString().take(8)
        _state.update { s ->
            s.copy(
                categories = s.categories + EventCategory(id, name, color),
                activeCategoryIds = s.activeCategoryIds + id
            )
        }
        persistCategories()
    }

    fun updateCategory(id: String, name: String, color: Long) {
        _state.update { s ->
            s.copy(categories = s.categories.map { if (it.id == id) it.copy(name = name, color = color) else it })
        }
        persistCategories()
    }

    fun deleteCategory(id: String) {
        val current = _state.value
        if (current.categories.size <= 1) return
        val fallback = current.categories.first { it.id != id }.id
        _state.update { s ->
            val reassigned = s.events.map { if (it.categoryId == id) it.copy(categoryId = fallback) else it }
            s.copy(
                categories = s.categories.filter { it.id != id },
                events = reassigned,
                activeCategoryIds = s.activeCategoryIds - id
            )
        }
        persistCategories()
        persistEvents()
    }
}
