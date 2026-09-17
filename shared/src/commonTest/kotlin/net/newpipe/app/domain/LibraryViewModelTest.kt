package net.newpipe.app.domain

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The library is the backing store of the History, Playlists, Watch later and
 * Downloads tabs, so every action behind those buttons is covered here.
 */
class LibraryViewModelTest {

    private var now = 1_000L
    private fun newLibrary(settings: MapSettings = MapSettings()) =
        LibraryViewModel(settings) { now }

    @Test
    fun recordsAndDeduplicatesWatchHistory() {
        val library = newLibrary()

        library.recordWatch("url-1", "First")
        now += 10
        library.recordWatch("url-2", "Second")
        now += 10
        library.recordWatch("url-1", "First")

        val history = library.history.value
        assertEquals(2, history.size)
        assertEquals("url-1", history.first().url, "the most recent video comes first")
        assertEquals(1, history.count { it.url == "url-1" })
    }

    @Test
    fun keepsResumePositionAcrossRewatch() {
        val library = newLibrary()
        library.recordWatch("url-1", "First")

        library.updatePosition("url-1", positionMs = 42_000, durationMs = 120_000)
        library.recordWatch("url-1", "First")

        val entry = library.history.value.single()
        assertEquals(42_000, entry.positionMs)
        assertEquals(120_000, entry.durationMs)
    }

    @Test
    fun disablingHistoryStopsRecording() {
        val library = newLibrary()
        library.setHistoryEnabled(false)

        library.recordWatch("url-1", "First")
        library.recordSearch("cats")

        assertTrue(library.history.value.isEmpty())
        assertTrue(library.searchHistory.value.isEmpty())
    }

    @Test
    fun clearingHistoryEmptiesTheList() {
        val library = newLibrary()
        library.recordWatch("url-1", "First")
        library.recordWatch("url-2", "Second")

        library.removeFromHistory("url-1")
        assertEquals(listOf("url-2"), library.history.value.map { it.url })

        library.clearHistory()
        assertTrue(library.history.value.isEmpty())
    }

    @Test
    fun playlistsAreCreatedRenamedFilledAndDeleted() {
        val library = newLibrary()
        val playlist = library.createPlaylist("Music")
        val item = PlaylistItem(url = "url-1", title = "Song")

        library.addToPlaylist(playlist.id, item)
        library.addToPlaylist(playlist.id, item)
        assertEquals(1, library.playlists.value.single().items.size, "duplicates are ignored")

        library.renamePlaylist(playlist.id, "Best music")
        assertEquals("Best music", library.playlists.value.single().name)

        library.removeFromPlaylist(playlist.id, "url-1")
        assertTrue(library.playlists.value.single().items.isEmpty())

        library.deletePlaylist(playlist.id)
        assertTrue(library.playlists.value.isEmpty())
    }

    @Test
    fun creatingTheSamePlaylistTwiceReusesIt() {
        val library = newLibrary()
        val first = library.createPlaylist("Music")
        val second = library.createPlaylist("music")

        assertEquals(first.id, second.id)
        assertEquals(1, library.playlists.value.size)
    }

    @Test
    fun watchLaterTogglesBothWays() {
        val library = newLibrary()
        val item = PlaylistItem(url = "url-1", title = "Song")

        library.toggleWatchLater(item)
        assertTrue(library.isInWatchLater("url-1"))

        library.toggleWatchLater(item)
        assertFalse(library.isInWatchLater("url-1"))
    }

    @Test
    fun searchHistoryKeepsTheLatestQueryFirst() {
        val library = newLibrary()

        library.recordSearch("cats")
        library.recordSearch("dogs")
        library.recordSearch("cats")

        assertEquals(listOf("cats", "dogs"), library.searchHistory.value)

        library.removeSearch("dogs")
        assertEquals(listOf("cats"), library.searchHistory.value)

        library.clearSearchHistory()
        assertTrue(library.searchHistory.value.isEmpty())
    }

    @Test
    fun downloadsAreRecordedAndCleared() {
        val library = newLibrary()

        library.recordDownload("video.mp4", "https://example.org/v", "Video", isAudioOnly = false)
        library.recordDownload("audio.m4a", "https://example.org/a", "Audio", isAudioOnly = true)

        assertEquals(listOf("audio.m4a", "video.mp4"), library.downloads.value.map { it.fileName })

        library.removeDownloadRecord("audio.m4a")
        assertEquals(listOf("video.mp4"), library.downloads.value.map { it.fileName })

        library.clearDownloads()
        assertTrue(library.downloads.value.isEmpty())
    }

    @Test
    fun everythingIsPersistedAcrossRestarts() {
        val settings = MapSettings()
        val library = newLibrary(settings)
        library.recordWatch("url-1", "First")
        library.createPlaylist("Music")
        library.toggleWatchLater(PlaylistItem(url = "url-2", title = "Song"))
        library.recordSearch("cats")

        val restarted = newLibrary(settings)

        assertEquals(listOf("url-1"), restarted.history.value.map { it.url })
        assertEquals(listOf("Music"), restarted.playlists.value.map { it.name })
        assertEquals(listOf("url-2"), restarted.watchLater.value.map { it.url })
        assertEquals(listOf("cats"), restarted.searchHistory.value)
    }

    @Test
    fun historyIsCappedToTheMostRecentEntries() {
        val library = newLibrary()
        repeat(LibraryViewModel.MAX_HISTORY + 10) { index ->
            now += 1
            library.recordWatch("url-$index", "Video $index")
        }

        assertEquals(LibraryViewModel.MAX_HISTORY, library.history.value.size)
        assertEquals(
            "url-${LibraryViewModel.MAX_HISTORY + 9}",
            library.history.value.first().url
        )
    }
}
