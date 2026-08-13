package top.iwesley.lyn.music.platform

import java.io.File
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.StandardOpenOption

internal class JvmAppInstanceLock private constructor(
    private val channel: FileChannel,
    private val fileLock: FileLock,
) : AutoCloseable {
    override fun close() {
        runCatching { fileLock.release() }
        runCatching { channel.close() }
    }

    companion object {
        fun tryAcquire(userHomeDirectory: File = File(System.getProperty("user.home"))): JvmAppInstanceLock? {
            val primaryLockFile = File(userHomeDirectory, ".lynmusic-app.lock")
            return runCatching {
                tryAcquireLockFile(primaryLockFile)
            }.getOrElse { error ->
                System.err.println("[Desktop][Startup][WARN] app-instance-lock primary failed path=${primaryLockFile.absolutePath}")
                error.printStackTrace()
                val fallbackLockFile = File(
                    System.getProperty("java.io.tmpdir"),
                    "lynmusic-${System.getProperty("user.name").orEmpty().ifBlank { "desktop" }}.lock",
                )
                tryAcquireLockFile(fallbackLockFile)
            }
        }

        private fun tryAcquireLockFile(lockFile: File): JvmAppInstanceLock? {
            System.err.println("[Desktop][Startup][DEBUG] app-instance-lock path=${lockFile.absolutePath}")
            lockFile.parentFile?.mkdirs()
            val channel = FileChannel.open(
                lockFile.toPath(),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
            )
            val lock = try {
                channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            } catch (error: Throwable) {
                System.err.println("[Desktop][Startup][ERROR] app-instance-lock failed path=${lockFile.absolutePath}")
                error.printStackTrace()
                channel.close()
                throw error
            }
            if (lock == null) {
                channel.close()
                return null
            }
            return JvmAppInstanceLock(channel, lock)
        }
    }
}
