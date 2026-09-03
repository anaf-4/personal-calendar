package com.personalcalendar.app.auth

import com.personalcalendar.app.data.CalendarEvent
import com.personalcalendar.app.data.EventCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class ApiUserDto(
    val id: String,
    val email: String? = null,
    val displayName: String? = null,
    val discordUsername: String? = null
) {
    fun toAuthUser() = AuthUser(id, email, displayName, discordUsername)
}

@Serializable
private data class AuthResponseDto(
    val ok: Boolean = true,
    val token: String? = null,
    val user: ApiUserDto? = null,
    val error: String? = null
)

@Serializable
private data class MeResponseDto(val user: ApiUserDto)

/**
 * The desktop app stores category color as a "#rrggbb" hex string; Android stores it as a
 * packed ARGB Long internally. This DTO is the wire format shared with the server so both
 * platforms agree on it, converting at the API boundary only.
 */
@Serializable
data class SyncCategoryDto(val id: String, val name: String, val color: String)

fun EventCategory.toDto() = SyncCategoryDto(id, name, "#" + (color and 0xFFFFFFL).toString(16).padStart(6, '0'))

fun SyncCategoryDto.toCategory(): EventCategory {
    val hex = color.removePrefix("#")
    val parsed = hex.toLongOrNull(16) ?: 0x9A9CA6L
    return EventCategory(id, name, 0xFF000000L or (parsed and 0xFFFFFFL))
}

@Serializable
private data class SyncBodyDto(
    val events: List<CalendarEvent> = emptyList(),
    val categories: List<SyncCategoryDto> = emptyList()
)

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Failure(val error: String) : ApiResult<Nothing>()
}

class AuthApi(private val serverUrlProvider: suspend () -> String, private val tokenProvider: suspend () -> String?) {
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun request(
        path: String,
        method: String,
        bodyJson: String? = null,
        withAuth: Boolean = false
    ): Pair<Int, String> = withContext(Dispatchers.IO) {
        val base = serverUrlProvider()
        val connection = (URL("$base$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Content-Type", "application/json")
            if (withAuth) {
                val token = tokenProvider()
                if (token != null) setRequestProperty("Authorization", "Bearer $token")
            }
            if (bodyJson != null) {
                doOutput = true
                outputStream.use { it.write(bodyJson.toByteArray(Charsets.UTF_8)) }
            }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.readText() ?: ""
        code to text
    }

    suspend fun register(email: String, password: String, displayName: String?): ApiResult<Pair<String, AuthUser>> {
        val body = json.encodeToString(RegisterRequest(email, password, displayName))
        return runCatching {
            val (code, text) = request("/api/auth/register", "POST", body)
            val parsed = json.decodeFromString<AuthResponseDto>(text)
            if (code in 200..299 && parsed.token != null && parsed.user != null) {
                ApiResult.Success(parsed.token to parsed.user.toAuthUser())
            } else {
                ApiResult.Failure(parsed.error ?: "register_failed")
            }
        }.getOrElse { ApiResult.Failure("network_error") }
    }

    suspend fun login(email: String, password: String): ApiResult<Pair<String, AuthUser>> {
        val body = json.encodeToString(LoginRequest(email, password))
        return runCatching {
            val (code, text) = request("/api/auth/login", "POST", body)
            val parsed = json.decodeFromString<AuthResponseDto>(text)
            if (code in 200..299 && parsed.token != null && parsed.user != null) {
                ApiResult.Success(parsed.token to parsed.user.toAuthUser())
            } else {
                ApiResult.Failure(parsed.error ?: "login_failed")
            }
        }.getOrElse { ApiResult.Failure("network_error") }
    }

    suspend fun me(): ApiResult<AuthUser> {
        return runCatching {
            val (code, text) = request("/api/auth/me", "GET", withAuth = true)
            if (code in 200..299) {
                ApiResult.Success(json.decodeFromString<MeResponseDto>(text).user.toAuthUser())
            } else {
                ApiResult.Failure("me_failed")
            }
        }.getOrElse { ApiResult.Failure("network_error") }
    }

    suspend fun pull(): ApiResult<Pair<List<CalendarEvent>, List<EventCategory>>> {
        return runCatching {
            val (code, text) = request("/sync", "GET", withAuth = true)
            if (code in 200..299) {
                val parsed = json.decodeFromString<SyncBodyDto>(text)
                ApiResult.Success(parsed.events to parsed.categories.map { it.toCategory() })
            } else {
                ApiResult.Failure("pull_failed")
            }
        }.getOrElse { ApiResult.Failure("network_error") }
    }

    suspend fun push(events: List<CalendarEvent>, categories: List<EventCategory>): ApiResult<Unit> {
        val body = json.encodeToString(SyncBodyDto(events, categories.map { it.toDto() }))
        return runCatching {
            val (code, _) = request("/sync", "PUT", body, withAuth = true)
            if (code in 200..299) ApiResult.Success(Unit) else ApiResult.Failure("push_failed")
        }.getOrElse { ApiResult.Failure("network_error") }
    }
}

@Serializable
private data class RegisterRequest(val email: String, val password: String, val displayName: String?)

@Serializable
private data class LoginRequest(val email: String, val password: String)
