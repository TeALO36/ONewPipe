package net.newpipe.app.domain

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.russhwolf.settings.Settings
import fr.arthonetwork.onewpipe.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class GitHubRelease(
    val tag_name: String,
    val html_url: String,
    val prerelease: Boolean = false
) {
    val versionName: String get() = tag_name.removePrefix("v")
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: GitHubRelease) : UpdateState
    data class Error(val message: String) : UpdateState
}

/** Checks the public ONewPipe GitHub release feed without downloading binaries. */
class UpdateViewModel(private val settings: Settings) : ViewModel() {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
        }
    }

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    fun checkForUpdates(force: Boolean = false) {
        if (!force && nowMillis() - settings.getLong(LAST_CHECK_KEY, 0) < CHECK_INTERVAL_MILLIS) return
        if (_state.value is UpdateState.Checking) return
        _state.value = UpdateState.Checking
        viewModelScope.launch {
            _state.value = runCatching {
                val response = client.get(RELEASE_URL)
                if (!response.status.isSuccess()) {
                    error("GitHub returned HTTP ${response.status.value}")
                }
                settings.putLong(LAST_CHECK_KEY, nowMillis())
                val release = response.body<GitHubRelease>()
                if (isNewerVersion(release.versionName, BuildConfig.VERSION_NAME)) {
                    UpdateState.Available(release)
                } else {
                    UpdateState.UpToDate
                }
            }.getOrElse { error ->
                UpdateState.Error(error.message ?: "Unable to check for updates")
            }
        }
    }

    override fun onCleared() {
        client.close()
        super.onCleared()
    }

    companion object {
        const val RELEASE_URL = "https://api.github.com/repos/TeALO36/ONewPipe/releases/latest"
        private const val LAST_CHECK_KEY = "update_last_check_millis"
        private const val CHECK_INTERVAL_MILLIS = 6 * 60 * 60 * 1_000L

        private fun nowMillis(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()

        /** Compares stable dotted versions while ignoring a leading `v` and suffixes. */
        fun isNewerVersion(candidate: String, current: String): Boolean {
            fun parts(version: String): List<Int> = version
                .removePrefix("v")
                .substringBefore('-')
                .split('.')
                .map { it.toIntOrNull() ?: 0 }

            val candidateParts = (parts(candidate) + List(3) { 0 }).take(3)
            val currentParts = (parts(current) + List(3) { 0 }).take(3)
            return candidateParts.indices.firstOrNull { candidateParts[it] != currentParts[it] }
                ?.let { candidateParts[it] > currentParts[it] }
                ?: false
        }
    }
}
