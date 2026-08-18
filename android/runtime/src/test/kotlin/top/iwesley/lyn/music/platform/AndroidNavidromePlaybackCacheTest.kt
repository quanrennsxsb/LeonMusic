package top.iwesley.lyn.music.platform

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidNavidromePlaybackCacheTest {
    @Test
    fun `known content length requires complete cache from start`() {
        assertTrue(
            hasPlayableAndroidNavidromePlaybackCache(
                cachedFromStartBytes = 12_000L,
                contentLengthBytes = 12_000L,
            ),
        )
        assertFalse(
            hasPlayableAndroidNavidromePlaybackCache(
                cachedFromStartBytes = 11_999L,
                contentLengthBytes = 12_000L,
            ),
        )
    }

    @Test
    fun `unknown content length requires a useful contiguous prefix`() {
        assertTrue(
            hasPlayableAndroidNavidromePlaybackCache(
                cachedFromStartBytes = 1024L * 1024L,
                contentLengthBytes = -1L,
            ),
        )
        assertFalse(
            hasPlayableAndroidNavidromePlaybackCache(
                cachedFromStartBytes = 1024L,
                contentLengthBytes = -1L,
            ),
        )
    }
}
