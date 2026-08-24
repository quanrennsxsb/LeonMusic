package top.iwesley.lyn.music.tv

import android.view.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import top.iwesley.lyn.music.feature.player.PlayerIntent

class TvPlayerMediaKeyTest {
    @Test
    fun mapsPlayPauseAndTrackKeysToPlayerIntents() {
        assertEquals(
            PlayerIntent.TogglePlayPause,
            tvPlayerIntentForMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, isPlaying = false),
        )
        assertEquals(
            PlayerIntent.SkipPrevious,
            tvPlayerIntentForMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS, isPlaying = true),
        )
        assertEquals(
            PlayerIntent.SkipNext,
            tvPlayerIntentForMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT, isPlaying = true),
        )
    }

    @Test
    fun honorsDedicatedPlayAndPauseKeysWithoutTogglingTheWrongWay() {
        assertEquals(
            PlayerIntent.ResumeCurrentTrackPlayback,
            tvPlayerIntentForMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY, isPlaying = false),
        )
        assertNull(tvPlayerIntentForMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY, isPlaying = true))

        assertEquals(
            PlayerIntent.TogglePlayPause,
            tvPlayerIntentForMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE, isPlaying = true),
        )
        assertNull(tvPlayerIntentForMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE, isPlaying = false))
    }

    @Test
    fun ignoresUnrelatedKeys() {
        assertNull(tvPlayerIntentForMediaKey(KeyEvent.KEYCODE_DPAD_CENTER, isPlaying = true))
    }
}
