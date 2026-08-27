package top.iwesley.lyn.music.platform

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import top.iwesley.lyn.music.core.model.JvmAppDataDirectory

/**
 * Extracts the bundled bridge once per bridge binary version.
 *
 * JNA identifies native libraries by their path. Using a fresh temporary path for every
 * WidgetKit refresh therefore creates another native-library mapping that cannot be released
 * while the JVM is running.
 */
internal fun macOsNowPlayingBridgeLibraryPath(): Path = MacOsNowPlayingBridgeLibrary.path

private object MacOsNowPlayingBridgeLibrary {
    val path: Path by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val resourceName = "/native/macos/libLeonMusicNowPlayingBridge.dylib"
        val bytes = requireNotNull(MacOsNowPlayingBridgeLibrary::class.java.getResourceAsStream(resourceName)) {
            "Missing resource $resourceName"
        }.use { it.readBytes() }
        val checksum = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
        val directory = JvmAppDataDirectory.resolve("native").toPath()
        val target = directory.resolve("libLeonMusicNowPlayingBridge-$checksum.dylib")
        Files.createDirectories(directory)
        if (Files.isRegularFile(target) && Files.size(target) == bytes.size.toLong()) {
            return@lazy target
        }
        val temporary = Files.createTempFile(directory, "now-playing-", ".tmp")
        try {
            Files.write(temporary, bytes)
            try {
                Files.move(
                    temporary,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
        target
    }
}
