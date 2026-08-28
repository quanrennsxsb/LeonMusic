package top.iwesley.lyn.music.platform

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import top.iwesley.lyn.music.core.model.isArtworkPayloadSizeAllowed
import top.iwesley.lyn.music.core.model.isArtworkSourceDimensionsAllowed
import top.iwesley.lyn.music.core.model.navidromeArtworkDifferenceHash

internal fun decodeSkiaArtworkDifferenceHash(bytes: ByteArray): ULong? {
    return runCatching {
        if (!isArtworkPayloadSizeAllowed(bytes.size.toLong())) return@runCatching null
        val image = Image.makeFromEncoded(bytes)
        if (!isArtworkSourceDimensionsAllowed(image.width, image.height)) {
            image.close()
            return@runCatching null
        }
        val bitmap = Bitmap()
        if (!bitmap.allocN32Pixels(9, 8)) {
            bitmap.close()
            image.close()
            return@runCatching null
        }
        try {
            Canvas(bitmap).drawImageRect(
                image,
                Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
                Rect.makeWH(9f, 8f),
            )
            navidromeArtworkDifferenceHash(
                IntArray(9 * 8) { index ->
                    val x = index % 9
                    val y = index / 9
                    bitmap.getColor(x, y).argbLuminance()
                },
            )
        } finally {
            bitmap.close()
            image.close()
        }
    }.getOrNull()
}

private fun Int.argbLuminance(): Int {
    val red = (this shr 16) and 0xFF
    val green = (this shr 8) and 0xFF
    val blue = this and 0xFF
    return (red * 299 + green * 587 + blue * 114) / 1000
}
