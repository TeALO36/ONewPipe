package net.newpipe.app.domain

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration test for [ServerClient] against a running ONewPipe server.
 * Skips (passes) when no server is reachable, so it is safe in CI.
 *
 * Start one locally with:
 *   ./gradlew :server:run
 * or point at a deployed instance with the ONE_PIPE_SERVER_URL env var.
 */
class ServerClientTest {

    private val serverUrl: String = System.getenv("ONEWPIPE_SERVER_URL")
        ?: "http://localhost:8099"

    /**
     * A real ONewPipe server answers 401 for bad credentials. Anything else
     * (connection refused, 404 from another service on the port) means there
     * is no ONewPipe server to test against.
     */
    private fun serverReachable(): Boolean = runBlocking {
        val error = runCatching {
            val client = ServerClient()
            client.login(serverUrl, "no-such-user", "no-such-pass")
        }.exceptionOrNull()
        // 401 Unauthorized = a real ONewPipe server answering with bad credentials.
        error is ServerException && error.statusCode == 401
    }

    @Test
    fun normalizesBareLanAddress() {
        val client = ServerClient()
        assertEquals("http://192.168.1.10:8080", client.normalizeServerUrl("192.168.1.10"))
        assertEquals("http://192.168.1.10:8099", client.normalizeServerUrl("192.168.1.10:8099"))
        assertEquals("https://server.example:8443", client.normalizeServerUrl("https://server.example:8443/"))
    }

    @Test
    fun registerPushAndPullWatchState() = runBlocking {
        if (!serverReachable()) return@runBlocking // no server: skip

        val client = ServerClient()
        val username = "test_" + kotlin.random.Random.nextLong(0, 1_000_000)
        val password = "secret-password"

        val auth = client.register(serverUrl, username, password)
        assertEquals(username, auth.username)
        assertTrue(auth.token.isNotBlank())

        val config = ServerConfig(
            serverUrl = serverUrl,
            username = auth.username,
            token = auth.token
        )

        val url = "https://www.youtube.com/watch?v=test$username"
        val synced = client.pushWatchState(
            config,
            listOf(
                WatchStateItem(
                    url = url,
                    title = "Sync test video",
                    positionMs = 123_456,
                    durationMs = 1_000_000,
                    updatedAt = System.currentTimeMillis()
                )
            )
        )
        assertEquals(1, synced)

        val pulled = client.pullWatchState(config)
        val mine = pulled.firstOrNull { it.url == url }
        assertTrue(mine != null, "Pulled watch state should contain the pushed item")
        assertEquals(123_456, mine.positionMs)
        assertEquals(1_000_000, mine.durationMs)
    }
}
