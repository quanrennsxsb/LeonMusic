package top.iwesley.lyn.music.platform

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileHandle
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.fileHandleForReadingFromURL
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.fwrite
import platform.posix.memcpy
import top.iwesley.lyn.music.core.model.MAX_ARTWORK_PAYLOAD_BYTES
import top.iwesley.lyn.music.core.model.isArtworkPayloadSizeAllowed
import top.iwesley.lyn.music.core.model.readArtworkPayloadWithLimitSuspending

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal suspend fun readIosRemoteBytes(target: String): ByteArray? =
    runCatching { readIosRemoteBytesOrThrow(target) }.getOrNull()

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal suspend fun readIosRemoteBytesOrThrow(target: String): ByteArray = withContext(Dispatchers.Default) {
    if (NSURL.URLWithString(target) == null) {
        error("远端封面 URL 无效。")
    }
    iosArtworkHttpClient.prepareGet(target).execute { response ->
        if (!response.status.isSuccess()) {
            error("远端封面读取失败，HTTP ${response.status.value}。")
        }
        response.headers[HttpHeaders.ContentLength]
            ?.toLongOrNull()
            ?.let { contentLength ->
                if (!isArtworkPayloadSizeAllowed(contentLength)) {
                    error("远端封面大小超出限制。")
                }
            }
        val channel = response.bodyAsChannel()
        readArtworkPayloadWithLimitSuspending { buffer ->
            channel.readAvailable(buffer)
        } ?: error("远端封面大小超出限制。")
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun readIosArtworkLocalBytes(path: String): ByteArray? {
    return readIosLocalBytesWithLimit(path, MAX_ARTWORK_PAYLOAD_BYTES.toLong())
}

@OptIn(ExperimentalForeignApi::class)
internal fun readIosLocalBytes(path: String): ByteArray? {
    return readIosLocalBytesWithLimit(path, maxBytes = null)
}

@OptIn(ExperimentalForeignApi::class)
private fun readIosLocalBytesWithLimit(path: String, maxBytes: Long?): ByteArray? {
    val file = fopen(path, "rb") ?: return null
    return try {
        if (fseek(file, 0, SEEK_END) != 0) return null
        val byteCount = ftell(file)
        if (byteCount < 0L || byteCount > Int.MAX_VALUE.toLong()) return null
        if (maxBytes != null && !isArtworkPayloadSizeAllowed(byteCount, maxBytes)) return null
        if (fseek(file, 0, SEEK_SET) != 0) return null
        val byteCountInt = byteCount.toInt()
        val byteArray = ByteArray(byteCountInt)
        val bytesRead = byteArray.usePinned { pinned ->
            fread(
                pinned.addressOf(0).reinterpret<ByteVar>(),
                1.convert(),
                byteCountInt.convert(),
                file,
            ).toInt()
        }
        if (bytesRead != byteCountInt) return null
        byteArray
    } finally {
        fclose(file)
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun readIosFileBytesUpTo(url: NSURL, maxBytes: Long): ByteArray? {
    require(maxBytes in 1 until Int.MAX_VALUE.toLong())
    val capacity = (maxBytes + 1L).toInt()
    val output = ByteArray(capacity)
    return memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        val handle = NSFileHandle.fileHandleForReadingFromURL(url, error = error.ptr) ?: return@memScoped null
        try {
            var totalBytesRead = 0
            while (totalBytesRead < capacity) {
                error.value = null
                val requestedBytes = minOf(IOS_BOUNDED_READ_CHUNK_BYTES, capacity - totalBytesRead)
                val data = handle.readDataUpToLength(requestedBytes.toULong(), error = error.ptr)
                    ?: return@memScoped null
                if (error.value != null) return@memScoped null
                val chunk = data.toByteArray()
                if (chunk.isEmpty()) break
                chunk.copyInto(output, destinationOffset = totalBytesRead)
                totalBytesRead += chunk.size
            }
            if (totalBytesRead > maxBytes) null else output.copyOf(totalBytesRead)
        } finally {
            handle.closeAndReturnError(null)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun writeIosFileBytes(path: String, bytes: ByteArray): Boolean {
    val file = fopen(path, "wb") ?: return false
    return try {
        val written = bytes.usePinned { pinned ->
            fwrite(
                pinned.addressOf(0),
                1.convert(),
                bytes.size.convert(),
                file,
            ).toInt()
        }
        written == bytes.size
    } finally {
        fclose(file)
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun filePathFromIosLocator(target: String): String {
    return if (target.startsWith("file://", ignoreCase = true)) {
        NSURL.URLWithString(target)?.path ?: target.removePrefix("file://")
    } else {
        target
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun NSData.toByteArray(): ByteArray {
    val byteCount = length.toInt()
    if (byteCount <= 0) return ByteArray(0)
    val byteArray = ByteArray(byteCount)
    byteArray.usePinned { pinned ->
        memcpy(pinned.addressOf(0), bytes, length)
    }
    return byteArray
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.convert())
    }
}

private const val IOS_BOUNDED_READ_CHUNK_BYTES = 64 * 1024
private const val IOS_ARTWORK_NETWORK_TIMEOUT_MILLIS = 15_000L

private val iosArtworkHttpClient = HttpClient(Darwin) {
    install(HttpTimeout) {
        requestTimeoutMillis = IOS_ARTWORK_NETWORK_TIMEOUT_MILLIS
        connectTimeoutMillis = IOS_ARTWORK_NETWORK_TIMEOUT_MILLIS
        socketTimeoutMillis = IOS_ARTWORK_NETWORK_TIMEOUT_MILLIS
    }
}
