package top.iwesley.lyn.music.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class JvmWidgetLyricsPlatformServiceTest {

    @Test
    fun `service writes lyrics and reloads widget timeline on macos`() = runTest {
        val store = RecordingWidgetLyricsWriter()
        var reloadCount = 0
        val service = JvmWidgetLyricsPlatformService(
            store = store,
            reloadWidgetTimeline = { reloadCount += 1 },
            osName = "Mac OS X",
        )

        service.updateLyrics("Current line")

        assertEquals(listOf<String?>("Current line"), store.updates)
        assertEquals(1, reloadCount)
    }

    @Test
    fun `service skips writes and reloads outside macos`() = runTest {
        val store = RecordingWidgetLyricsWriter()
        var reloadCount = 0
        val service = JvmWidgetLyricsPlatformService(
            store = store,
            reloadWidgetTimeline = { reloadCount += 1 },
            osName = "Linux",
        )

        service.updateLyrics("Current line")

        assertEquals(emptyList<String?>(), store.updates)
        assertEquals(0, reloadCount)
    }

    @Test
    fun `service ignores unchanged widget lyrics on macos`() = runTest {
        val store = RecordingWidgetLyricsWriter()
        var reloadCount = 0
        val service = JvmWidgetLyricsPlatformService(
            store = store,
            reloadWidgetTimeline = { reloadCount += 1 },
            osName = "Mac OS X",
        )

        service.updateLyrics(" Current line ")
        service.updateLyrics("Current line")

        assertEquals(listOf<String?>("Current line"), store.updates)
        assertEquals(1, reloadCount)
    }

    private class RecordingWidgetLyricsWriter : JvmMacOsWidgetLyricsWriter {
        val updates = mutableListOf<String?>()

        override fun updateLyrics(text: String?) {
            updates += text
        }
    }
}
