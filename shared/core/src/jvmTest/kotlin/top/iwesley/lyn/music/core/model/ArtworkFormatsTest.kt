package top.iwesley.lyn.music.core.model

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArtworkFormatsTest {

    @Test
    fun `complete artwork payload accepts jpeg with end marker`() {
        assertTrue(
            isCompleteArtworkPayload(
                byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00, 0xFF.toByte(), 0xD9.toByte()),
            ),
        )
    }

    @Test
    fun `complete artwork payload rejects truncated jpeg`() {
        assertFalse(
            isCompleteArtworkPayload(
                byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00),
            ),
        )
    }

    @Test
    fun `complete artwork payload accepts png with iend marker`() {
        assertTrue(
            isCompleteArtworkPayload(
                byteArrayOf(
                    0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                    0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44,
                    0xAE.toByte(), 0x42, 0x60, 0x82.toByte(),
                ),
            ),
        )
    }

    @Test
    fun `complete artwork payload rejects truncated png`() {
        assertFalse(
            isCompleteArtworkPayload(
                byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A),
            ),
        )
    }

    @Test
    fun `complete artwork payload validates webp declared length`() {
        assertTrue(
            isCompleteArtworkPayload(
                byteArrayOf(
                    0x52, 0x49, 0x46, 0x46,
                    0x04, 0x00, 0x00, 0x00,
                    0x57, 0x45, 0x42, 0x50,
                ),
            ),
        )
        assertFalse(
            isCompleteArtworkPayload(
                byteArrayOf(
                    0x52, 0x49, 0x46, 0x46,
                    0x10, 0x00, 0x00, 0x00,
                    0x57, 0x45, 0x42, 0x50,
                ),
            ),
        )
    }

    @Test
    fun `complete artwork payload rejects unknown bytes`() {
        assertFalse(isCompleteArtworkPayload(byteArrayOf(1, 2, 3, 4)))
    }

    @Test
    fun `bounded artwork reader keeps a fragmented payload within its limit`() {
        val source = byteArrayOf(1, 2, 3, 4, 5)
        var offset = 0

        val payload = readArtworkPayloadWithLimit(maxBytes = 5) { buffer ->
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
    fun `bounded artwork reader rejects a payload larger than its limit`() {
        val source = byteArrayOf(1, 2, 3, 4, 5)
        var offset = 0

        val payload = readArtworkPayloadWithLimit(maxBytes = 4) { buffer ->
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

    @Test
    fun `artwork source dimensions reject excessive pixel counts`() {
        assertTrue(isArtworkSourceDimensionsAllowed(width = 4096, height = 4096))
        assertFalse(isArtworkSourceDimensionsAllowed(width = 4097, height = 4096))
        assertFalse(isArtworkSourceDimensionsAllowed(width = 0, height = 4096))
    }
}
