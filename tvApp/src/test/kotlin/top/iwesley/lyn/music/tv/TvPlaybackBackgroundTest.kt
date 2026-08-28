package top.iwesley.lyn.music.tv

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

class TvPlaybackBackgroundTest {

    @Test
    fun `remote background payload accepts unknown-length stream within limit`() {
        val source = byteArrayOf(1, 2, 3, 4, 5)

        val payload = readTvPlaybackRemoteArtworkPayload(
            input = ByteArrayInputStream(source),
            contentLength = -1L,
            maxBytes = source.size,
        )

        assertContentEquals(source, payload)
    }

    @Test
    fun `remote background payload rejects oversized declared and streamed content`() {
        assertNull(
            readTvPlaybackRemoteArtworkPayload(
                input = ByteArrayInputStream(byteArrayOf(1)),
                contentLength = 5L,
                maxBytes = 4,
            ),
        )
        assertNull(
            readTvPlaybackRemoteArtworkPayload(
                input = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5)),
                contentLength = -1L,
                maxBytes = 4,
            ),
        )
    }
}
