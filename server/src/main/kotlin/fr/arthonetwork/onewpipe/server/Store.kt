package fr.arthonetwork.onewpipe.server

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class AccountRecord(
    val username: String,
    val salt: String,
    val hash: String,
    val createdAt: Long = System.currentTimeMillis(),
    val isAdmin: Boolean = false
)

@Serializable
data class StoreFile(
    val accounts: List<AccountRecord> = emptyList(),
    val watchState: Map<String, Map<String, WatchStateItem>> = emptyMap(),
    val sessionRevokedBefore: Map<String, Long> = emptyMap()
)

/**
 * Tiny file-based store. Data lives in [dataDir] (env `DATA_DIR`, default `./data`).
 * Not a full database: it is intentionally simple, synchronous and safe enough for
 * a personal, self-hosted instance.
 */
class Store(dataDir: File) {

    private val file = dataDir.resolve("store.json")
    private val json = Json { prettyPrint = true }
    private val lock = Object()

    private val accounts = ConcurrentHashMap<String, AccountRecord>()
    private val watchState = ConcurrentHashMap<String, ConcurrentHashMap<String, WatchStateItem>>()
    private val sessionRevokedBefore = ConcurrentHashMap<String, Long>()

    init {
        dataDir.mkdirs()
        load()
    }

    private fun load() {
        synchronized(lock) {
            if (!file.exists()) return
            try {
                val data = json.decodeFromString<StoreFile>(file.readText())
                data.accounts.forEach { accounts[it.username] = it }
                data.watchState.forEach { (user, items) ->
                    watchState[user] = ConcurrentHashMap(items)
                }
                sessionRevokedBefore.putAll(data.sessionRevokedBefore)
            } catch (e: Exception) {
                System.err.println("WARNING: could not read $file: ${e.message}")
            }
        }
    }

    private fun persist() {
        synchronized(lock) {
            val data = StoreFile(
                accounts = accounts.values.sortedBy { it.createdAt },
                watchState = watchState.mapValues { it.value.toMap() },
                sessionRevokedBefore = sessionRevokedBefore.toMap()
            )
            val tmp = File(file.path + ".tmp")
            tmp.writeText(json.encodeToString(data))
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    // ---- Accounts ----

    fun findAccount(username: String): AccountRecord? = accounts[username]

    fun createAccount(record: AccountRecord): Boolean {
        synchronized(lock) {
            if (accounts.containsKey(record.username)) return false
            val firstAccount = accounts.isEmpty()
            accounts[record.username] = record.copy(isAdmin = firstAccount || record.isAdmin)
            persist()
            return true
        }
    }

    fun isAdmin(username: String): Boolean {
        val account = accounts[username] ?: return false
        // Existing installations predate the role field: migrate the oldest
        // account as administrator without rewriting their credentials.
        return account.isAdmin || accounts.values.minByOrNull { it.createdAt }?.username == username
    }

    fun adminAccounts(): List<AccountRecord> =
        accounts.values.sortedBy { it.createdAt }

    fun watchStateCount(username: String): Int = watchState[username]?.size ?: 0

    fun updatePassword(username: String, password: String): Boolean {
        synchronized(lock) {
            val account = accounts[username] ?: return false
            val salt = Passwords.newSalt()
            accounts[username] = account.copy(salt = salt, hash = Passwords.hash(password, salt))
            sessionRevokedBefore[username] = System.currentTimeMillis()
            persist()
            return true
        }
    }

    fun deleteAccount(username: String): Boolean {
        synchronized(lock) {
            if (accounts.remove(username) == null) return false
            watchState.remove(username)
            sessionRevokedBefore.remove(username)
            persist()
            return true
        }
    }

    fun revokeSessions(username: String): Boolean {
        synchronized(lock) {
            if (!accounts.containsKey(username)) return false
            sessionRevokedBefore[username] = System.currentTimeMillis()
            persist()
            return true
        }
    }

    fun isSessionValid(username: String, issuedAt: Long): Boolean =
        issuedAt > sessionRevokedBefore.getOrDefault(username, 0L)

    fun storeFileBytes(): Long = file.length()

    fun backupJson(): ByteArray = synchronized(lock) {
        val data = StoreFile(
            accounts = accounts.values.sortedBy { it.createdAt },
            watchState = watchState.mapValues { it.value.toMap() },
            sessionRevokedBefore = sessionRevokedBefore.toMap()
        )
        json.encodeToString(data).toByteArray(Charsets.UTF_8)
    }

    // ---- Watch state ----

    fun upsertWatchState(username: String, items: List<WatchStateItem>): Int {
        synchronized(lock) {
            val userItems = watchState.getOrPut(username) { ConcurrentHashMap() }
            items.forEach { item ->
                if (item.url.isNotBlank()) {
                    userItems[item.url] = item
                }
            }
            persist()
            return items.size
        }
    }

    fun getWatchState(username: String): List<WatchStateItem> =
        watchState[username]?.values
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()
}
