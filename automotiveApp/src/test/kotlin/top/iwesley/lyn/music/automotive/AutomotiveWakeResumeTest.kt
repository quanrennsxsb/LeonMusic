package top.iwesley.lyn.music.automotive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import top.iwesley.lyn.music.core.model.PlaybackSnapshot
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.feature.player.PlayerState

class AutomotiveWakeResumeTest {

    @Test
    fun `remember for wake stores current track only while playing`() {
        assertEquals("track-1", wakeResumeTrackIdOrNull(playerState(trackId = "track-1", isPlaying = true)))
        assertEquals(null, wakeResumeTrackIdOrNull(playerState(trackId = "track-1", isPlaying = false)))
        assertEquals(null, wakeResumeTrackIdOrNull(playerState(trackId = null, isPlaying = true)))
    }

    @Test
    fun `resume after wake only triggers for matching paused hydrated track`() {
        assertTrue(
            shouldResumePlaybackAfterWake(
                pendingTrackId = "track-1",
                state = playerState(trackId = "track-1", isPlaying = false),
            ),
        )
        assertFalse(
            shouldResumePlaybackAfterWake(
                pendingTrackId = "track-1",
                state = playerState(trackId = "track-2", isPlaying = false),
            ),
        )
        assertFalse(
            shouldResumePlaybackAfterWake(
                pendingTrackId = "track-1",
                state = playerState(trackId = "track-1", isPlaying = true),
            ),
        )
        assertFalse(
            shouldResumePlaybackAfterWake(
                pendingTrackId = "track-1",
                state = playerState(trackId = "track-1", isPlaying = false, isHydratingPlayback = true),
            ),
        )
    }

    @Test
    fun `wake resume does not dispatch twice while resume command is in flight`() {
        assertFalse(
            shouldResumePlaybackAfterWake(
                pendingTrackId = "track-1",
                resumeDispatched = true,
                state = playerState(trackId = "track-1", isPlaying = false),
            ),
        )
    }

    @Test
    fun `wake resume retries while matching paused track waits for network`() {
        assertTrue(
            shouldResumePlaybackAfterWake(
                pendingTrackId = "track-1",
                resumeDispatched = true,
                state = playerState(
                    trackId = "track-1",
                    isPlaying = false,
                    errorMessage = "等待网络连接，网络恢复后将继续播放",
                ),
            ),
        )
    }

    @Test
    fun `wake resume stays pending while matching paused track is hydrating or waiting`() {
        assertTrue(
            shouldKeepWakeResumePending(
                pendingTrackId = "track-1",
                state = playerState(trackId = "track-1", isPlaying = false, isHydratingPlayback = true),
            ),
        )
        assertTrue(
            shouldKeepWakeResumePending(
                pendingTrackId = "track-1",
                resumeDispatched = true,
                state = playerState(trackId = "track-1", isPlaying = false),
            ),
        )
        assertTrue(
            shouldKeepWakeResumePending(
                pendingTrackId = "track-1",
                resumeDispatched = true,
                state = playerState(
                    trackId = "track-1",
                    isPlaying = false,
                    errorMessage = "等待网络连接，网络恢复后将继续播放",
                ),
            ),
        )
        assertFalse(
            shouldKeepWakeResumePending(
                pendingTrackId = "track-1",
                state = playerState(trackId = "track-1", isPlaying = false),
            ),
        )
        assertFalse(
            shouldKeepWakeResumePending(
                pendingTrackId = "track-1",
                state = playerState(trackId = "track-2", isPlaying = false, isHydratingPlayback = true),
            ),
        )
        assertFalse(
            shouldKeepWakeResumePending(
                pendingTrackId = "track-1",
                state = playerState(trackId = "track-1", isPlaying = true, isHydratingPlayback = true),
            ),
        )
    }
}

private fun playerState(
    trackId: String?,
    isPlaying: Boolean,
    isHydratingPlayback: Boolean = false,
    errorMessage: String? = null,
): PlayerState {
    val track = trackId?.let {
        Track(
            id = it,
            sourceId = "source",
            title = "Song",
            mediaLocator = "locator:$it",
            relativePath = "Song.mp3",
        )
    }
    return PlayerState(
        snapshot = PlaybackSnapshot(
            queue = listOfNotNull(track),
            currentIndex = if (track != null) 0 else -1,
            isPlaying = isPlaying,
            isHydratingPlayback = isHydratingPlayback,
            errorMessage = errorMessage,
        ),
    )
}
