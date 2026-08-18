package top.iwesley.lyn.music.platform

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.outputStream

internal object JvmMacOsWidgetNowPlayingStore {
    private const val GROUP_CONTAINER_ID = "group.top.iwesley.lyn.music"
    private const val SNAPSHOT_RELATIVE_PATH = "LeonMusicWidget/now-playing.json"
    private const val ARTWORK_RELATIVE_PATH = "LeonMusicWidget/current-artwork.image"
    private const val EMPTY_SNAPSHOT = """{"hasTrack":false,"updatedAtEpochSeconds":0}"""

    private val snapshotFile: Path? by lazy {
        resolveGroupContainerSnapshotFile()
    }

    fun update(payload: JvmNowPlayingPayload) {
        val file = snapshotFile ?: return
        val artworkPath = copyArtworkIntoSharedContainer(payload.artworkPath, file)
        writeSnapshot(
            file = file,
            json = buildString {
                append("{")
                append("\"hasTrack\":true")
                append(",\"title\":")
                appendJsonString(payload.title)
                append(",\"artist\":")
                appendJsonNullableString(payload.artist)
                append(",\"album\":")
                appendJsonNullableString(payload.album)
                append(",\"artworkPath\":")
                appendJsonNullableString(artworkPath)
                append(",\"durationMs\":")
                append(payload.durationMs.coerceAtLeast(0L))
                append(",\"positionMs\":")
                append(payload.positionMs.coerceAtLeast(0L))
                append(",\"isPlaying\":")
                append(payload.isPlaying)
                append(",\"canSeek\":")
                append(payload.canSeek)
                append(",\"hasNext\":")
                append(payload.hasNext)
                append(",\"hasPrevious\":")
                append(payload.hasPrevious)
                append(",\"updatedAtEpochSeconds\":")
                append(System.currentTimeMillis() / 1_000L)
                append("}")
            },
        )
    }

    fun clear() {
        val file = snapshotFile ?: return
        writeSnapshot(
            file = file,
            json = """{"hasTrack":false,"updatedAtEpochSeconds":${System.currentTimeMillis() / 1_000L}}""",
        )
    }

    private fun resolveGroupContainerSnapshotFile(): Path? {
        val home = System.getProperty("user.home")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return Path.of(home, "Library", "Group Containers", GROUP_CONTAINER_ID, SNAPSHOT_RELATIVE_PATH)
    }

    private fun copyArtworkIntoSharedContainer(artworkPath: String?, snapshotFile: Path): String? {
        val sourcePath = artworkPath
            ?.trim()
            ?.takeIf { it.startsWith("/") }
            ?.let { runCatching { Path.of(it) }.getOrNull() }
            ?.takeIf { Files.isRegularFile(it) }
            ?: return null
        val home = System.getProperty("user.home")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val target = Path.of(home, "Library", "Group Containers", GROUP_CONTAINER_ID, ARTWORK_RELATIVE_PATH)
        return runCatching {
            target.parent?.createDirectories()
            val temp = Files.createTempFile(snapshotFile.parent, "current-artwork-", ".tmp")
            try {
                Files.copy(sourcePath, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                Files.move(
                    temp,
                    target,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                )
            } finally {
                temp.deleteIfExists()
            }
            target.toString()
        }.getOrNull()
    }

    @OptIn(ExperimentalPathApi::class)
    private fun writeSnapshot(file: Path, json: String) {
        runCatching {
            file.parent?.createDirectories()
            val temp = Files.createTempFile(file.parent, "now-playing-", ".tmp")
            try {
                temp.outputStream().bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(json.ifBlank { EMPTY_SNAPSHOT })
                    writer.write("\n")
                }
                Files.move(
                    temp,
                    file,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                )
            } finally {
                temp.deleteIfExists()
            }
        }
    }
}

private fun StringBuilder.appendJsonNullableString(value: String?) {
    if (value == null) {
        append("null")
    } else {
        appendJsonString(value)
    }
}

private fun StringBuilder.appendJsonString(value: String) {
    append('"')
    value.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> {
                if (char.code < 0x20) {
                    append("\\u")
                    append(char.code.toString(16).padStart(4, '0'))
                } else {
                    append(char)
                }
            }
        }
    }
    append('"')
}
