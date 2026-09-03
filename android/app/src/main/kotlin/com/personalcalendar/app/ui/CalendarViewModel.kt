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
import com.personalcalendar.app.auth.ApiResult
import com.personalcalendar.app.auth.AuthApi
import com.personalcalendar.app.auth.AuthStore
import com.personalcalendar.app.auth.AuthUser
import com.personalcalendar.app.data.SettingsStore
import com.personalcalendar.app.data.expandOccurrences
import com.personalcalendar.app.reminders.ReminderScheduler
import com.personalcalendar.app.update.GithubRelease
import com.personalcalendar.app.update.UpdateChecker
import com.personalcalendar.app.update.UpdateInstaller
import java.security.MessageDigest
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    val updateDownloading: Boolean = false,
    val authUser: AuthUser? = null,
    val serverUrl: String = "",
    val authBusy: Boolean = false,
    val authError: String? = null,
    val hasPinSet: Boolean = false,
    val pinLocked: Boolean = false
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
    private val authStore = AuthStore(application)
    private val authApi = AuthApi(
        serverUrlProvider = { authStore.currentServerUrl() },
        tokenProvider = { authStore.currentToken() }
    )

    private val _state = MutableStateFlow(CalendarUiState())
    val state: StateFlow<CalendarUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val events = repository.loadEvents()
            val categories = repository.loadCategories()
            val pinHash = authStore.pinHash.first()
            val user = authStore.user.first()
            val serverUrl = authStore.currentServerUrl()

            _state.update {
                it.copy(
                    loading = false,
                    events = events,
                    categories = categories,
                    activeCategoryIds = categories.map { c -> c.id }.toSet(),
                    hasPinSet = pinHash != null,
                    pinLocked = pinHash != null,
                    authUser = user,
                    serverUrl = serverUrl
                )
            }
            events.forEach { ReminderScheduler.scheduleForEvent(getApplication(), it) }

            if (user != null) pullFromServer()

            launch {
                val release = UpdateChecker.checkLatest(BuildConfig.VERSION_NAME)
                if (release?.apkAsset != null) {
                    _state.update { it.copy(availableUpdate = release) }
                }
            }
            settingsStore.theme.collect { t -> _state.update { s -> s.copy(theme = t) } }
        }
    }

    // ---------- account / sync ----------

    fun setServerUrl(url: String) {
        _state.update { it.copy(serverUrl = url) }
        viewModelScope.launch { authStore.setServerUrl(url) }
    }

    fun register(email: String, password: String, displayName: String?) {
        _state.update { it.copy(authBusy = true, authError = null) }
        viewModelScope.launch {
            when (val result = authApi.register(email, password, displayName)) {
                is ApiResult.Success -> {
                    val (token, user) = result.data
                    authStore.saveSession(token, user)
                    _state.update { it.copy(authBusy = false, authUser = user) }
                    pushToServerIfLoggedIn()
                }
                is ApiResult.Failure -> _state.update { it.copy(authBusy = false, authError = result.error) }
            }
        }
    }

    fun login(email: String, password: String) {
        _state.update { it.copy(authBusy = true, authError = null) }
        viewModelScope.launch {
            when (val result = authApi.login(email, password)) {
                is ApiResult.Success -> {
                    val (token, user) = result.data
                    authStore.saveSession(token, user)
                    _state.update { it.copy(authBusy = false, authUser = user) }
                    pullFromServer()
                }
                is ApiResult.Failure -> _state.update { it.copy(authBusy = false, authError = result.error) }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authStore.clearSession()
            _state.update { it.copy(authUser = null) }
        }
    }

    fun discordLoginUrl(): String = "${_state.value.serverUrl}/auth/discord"

    /** Called by MainActivity when the app is reopened via the personalcalendar://auth-callback deep link. */
    fun handleDeepLinkToken(token: String) {
        viewModelScope.launch {
            val previousToken = authStore.currentToken()
            authStore.saveSession(token, AuthUser(id = "", email = null, displayName = null, discordUsername = null))
            when (val meResult = authApi.me()) {
                is ApiResult.Success -> {
                    authStore.saveSession(token, meResult.data)
                    _state.update { it.copy(authUser = meResult.data) }
                    pullFromServer()
                }
                is ApiResult.Failure -> {
                    if (previousToken != null) authStore.saveSession(previousToken, _state.value.authUser ?: AuthUser("", null, null, null))
                    else authStore.clearSession()
                    _state.update { it.copy(authError = "discord_login_failed") }
                }
            }
        }
    }

    private suspend fun pullFromServer() {
        when (val result = authApi.pull()) {
            is ApiResult.Success -> {
                val (events, categories) = result.data
                val localEvents = _state.value.events
                val localCategories = _state.value.categories

                if (events.isEmpty() && categories.isEmpty() && (localEvents.isNotEmpty() || localCategories.isNotEmpty())) {
                    // Fresh account with nothing saved yet — seed it from local data instead of
                    // wiping local with empty server data.
                    authApi.push(localEvents, localCategories)
                    return
                }

                repository.saveEvents(events)
                if (categories.isNotEmpty()) repository.saveCategories(categories)
                val finalCategories = if (categories.isNotEmpty()) categories else localCategories
                _state.update {
                    it.copy(
                        events = events,
                        categories = finalCategories,
                        activeCategoryIds = finalCategories.map { c -> c.id }.toSet()
                    )
                }
                events.forEach { ReminderScheduler.scheduleForEvent(getApplication(), it) }
            }
            is ApiResult.Failure -> { /* offline or unreachable; keep local data */ }
        }
    }

    private fun pushToServerIfLoggedIn() {
        if (_state.value.authUser == null) return
        val events = _state.value.events
        val categories = _state.value.categories
        viewModelScope.launch { authApi.push(events, categories) }
    }

    // ---------- PIN lock ----------

    private fun sha256(text: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest("pc-pin-salt:$text".toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun setPin(pin: String) {
        viewModelScope.launch {
            authStore.setPinHash(sha256(pin))
            _state.update { it.copy(hasPinSet = true) }
        }
    }

    fun clearPin() {
        viewModelScope.launch {
            authStore.setPinHash(null)
            _state.update { it.copy(hasPinSet = false) }
        }
    }

    suspend fun checkPin(pin: String): Boolean {
        val stored = authStore.pinHash.first()
        val correct = stored != null && stored == sha256(pin)
        if (correct) _state.update { it.copy(pinLocked = false) }
        return correct
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
        pushToServerIfLoggedIn()
    }

    private fun persistCategories() {
        val snapshot = _state.value.categories
        viewModelScope.launch { repository.saveCategories(snapshot) }
        pushToServerIfLoggedIn()
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
