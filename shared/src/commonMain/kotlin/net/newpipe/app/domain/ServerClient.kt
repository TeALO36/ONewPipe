package net.newpipe.app.domain

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Connection to a self-hosted ONewPipe server. */
@Serializable
data class ServerConfig(
    val serverUrl: String = "",
    val username: String = "",
    val token: String = ""
) {
    val isConnected: Boolean get() = token.isNotBlank() && serverUrl.isNotBlank()
}

@Serializable
data class WatchStateItem(
    val url: String,
    val title: String = "",
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val updatedAt: Long = 0
)

@Serializable
private data class AuthRequest(val username: String, val password: String)

/** Result of a successful register/login against the server. */
@Serializable
data class AuthResponse(val token: String, val username: String)

@Serializable
private data class WatchStateRequest(val items: List<WatchStateItem>)

@Serializable
private data class WatchStateResponse(val synced: Int = 0, val items: List<WatchStateItem> = emptyList())

@Serializable
private data class ErrorResponse(val error: String = "")

class ServerException(message: String) : Exception(message)

/**
 * Talks to the ONewPipe server (see the `server` Gradle module).
 * All functions are suspend and safe to call from any coroutine.
 */
class ServerClient {

    private val json = Json { ignoreUnknownKeys = true }

    private fun httpClient(): HttpClient = HttpClient {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 10_000
        }
    }

    private fun baseUrl(serverUrl: String): String = serverUrl.trim().trimEnd('/')

    suspend fun register(serverUrl: String, username: String, password: String): AuthResponse =
        auth("/api/register", serverUrl, username, password)

    suspend fun login(serverUrl: String, username: String, password: String): AuthResponse =
        auth("/api/login", serverUrl, username, password)

    private suspend fun auth(path: String, serverUrl: String, username: String, password: String): AuthResponse {
        val client = httpClient()
        return try {
            val response = client.post("${baseUrl(serverUrl)}$path") {
                contentType(ContentType.Application.Json)
                setBody(AuthRequest(username.trim(), password))
            }
            val body = response.body<String>()
            if (response.status.value !in 200..299) {
                val error = runCatching { json.decodeFromString<ErrorResponse>(body) }.getOrNull()?.error
                    ?: "Server error (${response.status.value})"
                throw ServerException(error)
            }
            json.decodeFromString<AuthResponse>(body)
        } finally {
            client.close()
        }
    }

    /** Push the local watch positions to the server. Returns the number synced. */
    suspend fun pushWatchState(config: ServerConfig, items: List<WatchStateItem>): Int {
        if (!config.isConnected) return 0
        val client = httpClient()
        return try {
            val response = client.post("${baseUrl(config.serverUrl)}/api/watchstate") {
                bearerAuth(config.token)
                contentType(ContentType.Application.Json)
                setBody(WatchStateRequest(items))
            }
            if (response.status.value !in 200..299) throw ServerException("Sync failed (${response.status.value})")
            json.decodeFromString<WatchStateResponse>(response.body<String>()).synced
        } finally {
            client.close()
        }
    }

    /** Pull all watch positions stored on the server. */
    suspend fun pullWatchState(config: ServerConfig): List<WatchStateItem> {
        if (!config.isConnected) return emptyList()
        val client = httpClient()
        return try {
            val response = client.get("${baseUrl(config.serverUrl)}/api/watchstate") {
                bearerAuth(config.token)
            }
            if (response.status.value !in 200..299) throw ServerException("Sync failed (${response.status.value})")
            json.decodeFromString<List<WatchStateItem>>(response.body<String>())
        } finally {
            client.close()
        }
    }
}
