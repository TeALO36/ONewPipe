package fr.arthonetwork.onewpipe.server

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdminStoreTest {
    @Test
    fun firstAccountBecomesAdministratorAndOldStoresMigrate() {
        val directory = Files.createTempDirectory("onewpipe-admin-test").toFile()
        val store = Store(directory)

        assertTrue(store.createAccount(AccountRecord("alice", "salt", "hash", createdAt = 1)))
        assertTrue(store.createAccount(AccountRecord("bob", "salt", "hash", createdAt = 2)))
        assertTrue(store.isAdmin("alice"))
        assertFalse(store.isAdmin("bob"))

        val legacyDirectory = Files.createTempDirectory("onewpipe-legacy-admin-test").toFile()
        val legacyStore = Store(legacyDirectory)
        assertTrue(legacyStore.createAccount(AccountRecord("oldest", "salt", "hash", createdAt = 1, isAdmin = false)))
        assertTrue(legacyStore.createAccount(AccountRecord("newer", "salt", "hash", createdAt = 2, isAdmin = false)))
        assertTrue(legacyStore.isAdmin("oldest"))
        assertFalse(legacyStore.isAdmin("newer"))
    }

    @Test
    fun passwordChangesAndSessionRevocationInvalidateOldTokens() {
        val directory = Files.createTempDirectory("onewpipe-session-test").toFile()
        val store = Store(directory)
        assertTrue(store.createAccount(AccountRecord("alice", "salt", "hash")))

        val issuedBeforeRevoke = System.currentTimeMillis() - 1_000
        assertTrue(store.isSessionValid("alice", issuedBeforeRevoke))
        assertTrue(store.revokeSessions("alice"))
        assertFalse(store.isSessionValid("alice", issuedBeforeRevoke))

        assertTrue(store.updatePassword("alice", "new-password"))
        assertFalse(store.isSessionValid("alice", issuedBeforeRevoke))
    }

    @Test
    fun deletingUserRemovesOnlyThatAccount() {
        val directory = Files.createTempDirectory("onewpipe-delete-test").toFile()
        val store = Store(directory)
        assertTrue(store.createAccount(AccountRecord("admin", "salt", "hash")))
        assertTrue(store.createAccount(AccountRecord("user", "salt", "hash")))

        assertTrue(store.deleteAccount("user"))
        assertFalse(store.findAccount("user") != null)
        assertTrue(store.isAdmin("admin"))
    }
}
