package top.iwesley.lyn.music.core.model

const val MAX_ARTWORK_PAYLOAD_BYTES = 20 * 1024 * 1024
const val MAX_ARTWORK_SOURCE_PIXEL_COUNT = 16_777_216L

fun isArtworkPayloadSizeAllowed(
    sizeBytes: Long,
    maxBytes: Long = MAX_ARTWORK_PAYLOAD_BYTES.toLong(),
): Boolean {
    return sizeBytes in 1L..maxBytes
}

fun isArtworkSourceDimensionsAllowed(
    width: Int,
    height: Int,
    maxPixelCount: Long = MAX_ARTWORK_SOURCE_PIXEL_COUNT,
): Boolean {
    if (width <= 0 || height <= 0 || maxPixelCount <= 0L) return false
    return width.toLong() <= maxPixelCount / height.toLong()
}

fun readArtworkPayloadWithLimit(
    maxBytes: Int = MAX_ARTWORK_PAYLOAD_BYTES,
    readChunk: (ByteArray) -> Int,
): ByteArray? {
    require(maxBytes > 0) { "maxBytes must be positive." }

    val buffer = ByteArray(minOf(8 * 1024, maxBytes))
    var payload = ByteArray(buffer.size)
    var payloadSize = 0
    while (true) {
        val bytesRead = readChunk(buffer)
        if (bytesRead < 0) break
        if (bytesRead == 0) continue
        if (bytesRead > buffer.size || bytesRead > maxBytes - payloadSize) return null

        val requiredSize = payloadSize + bytesRead
        if (requiredSize > payload.size) {
            val nextSize = minOf(
                maxBytes,
                maxOf(requiredSize, payload.size * 2),
            )
            payload = payload.copyOf(nextSize)
        }
        buffer.copyInto(payload, destinationOffset = payloadSize, endIndex = bytesRead)
        payloadSize = requiredSize
    }
    return if (payloadSize == payload.size) payload else payload.copyOf(payloadSize)
}

suspend fun readArtworkPayloadWithLimitSuspending(
    maxBytes: Int = MAX_ARTWORK_PAYLOAD_BYTES,
    readChunk: suspend (ByteArray) -> Int,
): ByteArray? {
    require(maxBytes > 0) { "maxBytes must be positive." }

    val buffer = ByteArray(minOf(8 * 1024, maxBytes))
    var payload = ByteArray(buffer.size)
    var payloadSize = 0
    while (true) {
        val bytesRead = readChunk(buffer)
        if (bytesRead < 0) break
        if (bytesRead == 0) continue
        if (bytesRead > buffer.size || bytesRead > maxBytes - payloadSize) return null

        val requiredSize = payloadSize + bytesRead
        if (requiredSize > payload.size) {
            val nextSize = minOf(
                maxBytes,
                maxOf(requiredSize, payload.size * 2),
            )
            payload = payload.copyOf(nextSize)
        }
        buffer.copyInto(payload, destinationOffset = payloadSize, endIndex = bytesRead)
        payloadSize = requiredSize
    }
    return if (payloadSize == payload.size) payload else payload.copyOf(payloadSize)
}
