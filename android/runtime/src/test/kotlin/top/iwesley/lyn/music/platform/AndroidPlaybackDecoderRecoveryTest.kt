package top.iwesley.lyn.music.platform

import androidx.media3.common.PlaybackException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidPlaybackDecoderRecoveryTest {

    @Test
    fun `MediaCodec decoder failure retries once`() {
        assertTrue(
            shouldRetryAndroidMediaCodecDecoderFailure(
                errorCode = PlaybackException.ERROR_CODE_DECODING_FAILED,
                hasMediaCodecDecoderCause = true,
                recoveryCount = 0,
            ),
        )
        assertFalse(
            shouldRetryAndroidMediaCodecDecoderFailure(
                errorCode = PlaybackException.ERROR_CODE_DECODING_FAILED,
                hasMediaCodecDecoderCause = true,
                recoveryCount = 1,
            ),
        )
    }

    @Test
    fun `non decoder failure does not retry`() {
        assertFalse(
            shouldRetryAndroidMediaCodecDecoderFailure(
                errorCode = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                hasMediaCodecDecoderCause = true,
                recoveryCount = 0,
            ),
        )
        assertFalse(
            shouldRetryAndroidMediaCodecDecoderFailure(
                errorCode = PlaybackException.ERROR_CODE_DECODING_FAILED,
                hasMediaCodecDecoderCause = false,
                recoveryCount = 0,
            ),
        )
    }
}
