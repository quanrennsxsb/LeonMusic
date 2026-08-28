package top.iwesley.lyn.music.core.model

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

class ArtworkPayloadLimitsTest {

    @Test
    fun `suspending bounded reader keeps fragmented payload within its limit`() = runBlocking {
        val source = byteArrayOf(1, 2, 3, 4, 5)
        var offset = 0

        val payload = readArtworkPayloadWithLimitSuspending(maxBytes = source.size) { buffer ->
            if (offset >= source.size) {
                -1
            } else {
                buffer[0] = source[offset]
                offset += 1
                1
            }
        }

        assertContentEquals(source, payload)
    }

    @Test
    fun `suspending bounded reader rejects payload larger than its limit`() = runBlocking {
        val source = byteArrayOf(1, 2, 3, 4, 5)
        var offset = 0

        val payload = readArtworkPayloadWithLimitSuspending(maxBytes = source.size - 1) { buffer ->
            if (offset >= source.size) {
                -1
            } else {
                buffer[0] = source[offset]
                offset += 1
                1
            }
        }

        assertNull(payload)
    }
}
