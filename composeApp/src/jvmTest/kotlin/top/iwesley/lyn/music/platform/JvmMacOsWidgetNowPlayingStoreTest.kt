package top.iwesley.lyn.music.platform

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JvmMacOsWidgetNowPlayingStoreTest {

    @Test
    fun `update writes widget snapshot and copies artwork into shared container`() {
        val groupContainer = Files.createTempDirectory("leonmusic-widget-group-")
        val sourceArtwork = Files.createTempFile("leonmusic-widget-artwork-", ".image")
        val artworkBytes = byteArrayOf(1, 2, 3, 4)
        Files.write(sourceArtwork, artworkBytes)
        val store = JvmMacOsWidgetNowPlayingStore(
            groupContainerDirectory = groupContainer,
            clockEpochSeconds = { 123L },
        )

        store.update(
            JvmNowPlayingPayload(
                title = "A \"Quoted\" Song\nLive",
                artist = "Artist",
                album = null,
                artworkPath = sourceArtwork.toString(),
                lyricsText = "First line\nSecond line",
                durationMs = 180_000L,
                positionMs = 42_000L,
                isPlaying = true,
                canSeek = true,
                hasNext = false,
                hasPrevious = true,
            ),
        )

        val artworkTarget = groupContainer.resolve("LeonMusicWidget/current-artwork.image")
        val snapshot = Files.readString(groupContainer.resolve("LeonMusicWidget/now-playing.json"))
        assertContentEquals(artworkBytes, Files.readAllBytes(artworkTarget))
        assertTrue(snapshot.contains(""""hasTrack":true"""))
        assertTrue(snapshot.contains(""""title":"A \"Quoted\" Song\nLive""""))
        assertTrue(snapshot.contains(""""artist":"Artist""""))
        assertTrue(snapshot.contains(""""album":null"""))
        assertTrue(snapshot.contains(""""artworkPath":"${artworkTarget.toString()}""""))
        assertTrue(snapshot.contains(""""lyricsText":"First line\nSecond line""""))
        assertTrue(snapshot.contains(""""durationMs":180000"""))
        assertTrue(snapshot.contains(""""positionMs":42000"""))
        assertTrue(snapshot.contains(""""isPlaying":true"""))
        assertTrue(snapshot.contains(""""canSeek":true"""))
        assertTrue(snapshot.contains(""""hasNext":false"""))
        assertTrue(snapshot.contains(""""hasPrevious":true"""))
        assertTrue(snapshot.contains(""""updatedAtEpochSeconds":123"""))
    }

    @Test
    fun `update preserves existing artwork and lyrics for same track when payload omits them`() {
        val groupContainer = Files.createTempDirectory("leonmusic-widget-group-")
        val widgetDirectory = groupContainer.resolve("LeonMusicWidget")
        Files.createDirectories(widgetDirectory)
        Files.writeString(
            widgetDirectory.resolve("now-playing.json"),
            """{"hasTrack":true,"title":"Song","artworkPath":"/tmp/current-artwork.image","lyricsText":"Current lyric"}""",
        )
        val store = JvmMacOsWidgetNowPlayingStore(
            groupContainerDirectory = groupContainer,
            clockEpochSeconds = { 234L },
        )

        store.update(
            JvmNowPlayingPayload(
                title = "Song",
                artist = null,
                album = null,
                artworkPath = null,
                durationMs = 1_000L,
                positionMs = 500L,
                isPlaying = true,
                canSeek = true,
                hasNext = false,
                hasPrevious = false,
            ),
        )

        val snapshot = Files.readString(widgetDirectory.resolve("now-playing.json"))
        assertTrue(snapshot.contains(""""artworkPath":"/tmp/current-artwork.image""""))
        assertTrue(snapshot.contains(""""lyricsText":"Current lyric""""))
        assertTrue(snapshot.contains(""""updatedAtEpochSeconds":234"""))
    }

    @Test
    fun `updateLyrics upserts lyrics into current widget snapshot`() {
        val groupContainer = Files.createTempDirectory("leonmusic-widget-group-")
        val store = JvmMacOsWidgetNowPlayingStore(
            groupContainerDirectory = groupContainer,
            clockEpochSeconds = { 345L },
        )
        store.update(
            JvmNowPlayingPayload(
                title = "Song",
                artist = "Artist",
                album = "Album",
                artworkPath = null,
                durationMs = 2_000L,
                positionMs = 1_000L,
                isPlaying = true,
                canSeek = true,
                hasNext = false,
                hasPrevious = false,
            ),
        )

        store.updateLyrics("Current line")

        val snapshot = Files.readString(groupContainer.resolve("LeonMusicWidget/now-playing.json"))
        assertTrue(snapshot.contains(""""title":"Song""""))
        assertTrue(snapshot.contains(""""lyricsText":"Current line""""))
        assertTrue(snapshot.contains(""""updatedAtEpochSeconds":345"""))
    }

    @Test
    fun `update ignores relative or missing artwork paths`() {
        val groupContainer = Files.createTempDirectory("leonmusic-widget-group-")
        val store = JvmMacOsWidgetNowPlayingStore(
            groupContainerDirectory = groupContainer,
            clockEpochSeconds = { 456L },
        )

        store.update(
            JvmNowPlayingPayload(
                title = "Song",
                artist = null,
                album = null,
                artworkPath = "relative-cover.jpg",
                durationMs = -1L,
                positionMs = -1L,
                isPlaying = false,
                canSeek = false,
                hasNext = false,
                hasPrevious = false,
            ),
        )

        val snapshot = Files.readString(groupContainer.resolve("LeonMusicWidget/now-playing.json"))
        assertFalse(Files.exists(groupContainer.resolve("LeonMusicWidget/current-artwork.image")))
        assertTrue(snapshot.contains(""""artworkPath":null"""))
        assertTrue(snapshot.contains(""""durationMs":0"""))
        assertTrue(snapshot.contains(""""positionMs":0"""))
        assertTrue(snapshot.contains(""""updatedAtEpochSeconds":456"""))
    }

    @Test
    fun `clear writes empty widget snapshot`() {
        val groupContainer = Files.createTempDirectory("leonmusic-widget-group-")
        val store = JvmMacOsWidgetNowPlayingStore(
            groupContainerDirectory = groupContainer,
            clockEpochSeconds = { 789L },
        )

        store.clear()

        val snapshot = Files.readString(groupContainer.resolve("LeonMusicWidget/now-playing.json")).trim()
        assertEquals("""{"hasTrack":false,"updatedAtEpochSeconds":789}""", snapshot)
    }

    @Test
    fun `clear waits for an in-flight lyrics update and remains the final snapshot`() {
        val groupContainer = Files.createTempDirectory("leonmusic-widget-group-")
        val lyricsWriteStarted = CountDownLatch(1)
        val allowLyricsWrite = CountDownLatch(1)
        val clearWriteStarted = CountDownLatch(1)
        val snapshotWriter: (Path, String) -> Unit = { file, json ->
            when {
                json.contains(""""lyricsText":"Latest lyric"""") -> {
                    lyricsWriteStarted.countDown()
                    check(allowLyricsWrite.await(5, TimeUnit.SECONDS))
                }

                json.contains(""""hasTrack":false""") -> clearWriteStarted.countDown()
            }
            Files.createDirectories(requireNotNull(file.parent))
            Files.writeString(file, json)
        }
        val lyricsStore = JvmMacOsWidgetNowPlayingStore(
            groupContainerDirectory = groupContainer,
            clockEpochSeconds = { 1_000L },
            snapshotWriter = snapshotWriter,
        )
        val clearStore = JvmMacOsWidgetNowPlayingStore(
            groupContainerDirectory = groupContainer,
            clockEpochSeconds = { 2_000L },
            snapshotWriter = snapshotWriter,
        )
        lyricsStore.update(
            JvmNowPlayingPayload(
                title = "Song",
                artist = null,
                album = null,
                artworkPath = null,
                durationMs = 1_000L,
                positionMs = 0L,
                isPlaying = true,
                canSeek = true,
                hasNext = false,
                hasPrevious = false,
            ),
        )

        val executor = Executors.newFixedThreadPool(2)
        try {
            val lyricsWrite = executor.submit { lyricsStore.updateLyrics("Latest lyric") }
            assertTrue(lyricsWriteStarted.await(5, TimeUnit.SECONDS))

            val clearStarted = CountDownLatch(1)
            val clear = executor.submit {
                clearStarted.countDown()
                clearStore.clear()
            }
            assertTrue(clearStarted.await(5, TimeUnit.SECONDS))
            assertFalse(clearWriteStarted.await(250, TimeUnit.MILLISECONDS))

            allowLyricsWrite.countDown()
            lyricsWrite.get(5, TimeUnit.SECONDS)
            clear.get(5, TimeUnit.SECONDS)
        } finally {
            allowLyricsWrite.countDown()
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }

        val snapshot = Files.readString(groupContainer.resolve("LeonMusicWidget/now-playing.json")).trim()
        assertEquals("""{"hasTrack":false,"updatedAtEpochSeconds":2000}""", snapshot)
    }
}
