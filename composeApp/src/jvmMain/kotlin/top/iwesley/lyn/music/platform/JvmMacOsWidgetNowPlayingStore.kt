package top.iwesley.lyn.music.platform

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.outputStream

internal interface JvmMacOsWidgetNowPlayingWriter {
    fun update(payload: JvmNowPlayingPayload)
    fun clear()
}

internal interface JvmMacOsWidgetPlaybackCommandReader {
    fun consumeCommand(): MacOsNowPlayingCommand?
}

internal interface JvmMacOsWidgetLyricsWriter {
    fun updateLyrics(text: String?)
}

internal class JvmMacOsWidgetNowPlayingStore(
    private val groupContainerDirectory: Path? = resolveDefaultGroupContainerDirectory(),
    private val clockEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000L },
    private val snapshotWriter: (Path, String) -> Unit = ::writeWidgetSnapshot,
) : JvmMacOsWidgetNowPlayingWriter, JvmMacOsWidgetLyricsWriter, JvmMacOsWidgetPlaybackCommandReader {
    companion object {
        private const val GROUP_CONTAINER_ID = "group.top.iwesley.lyn.music"
        private const val SNAPSHOT_RELATIVE_PATH = "LeonMusicWidget/now-playing.json"
        private const val COMMAND_RELATIVE_PATH = "LeonMusicWidget/playback-command.json"
        private const val ARTWORK_RELATIVE_PATH = "LeonMusicWidget/current-artwork.image"
        private val snapshotLock = Any()

        fun default(): JvmMacOsWidgetNowPlayingStore = JvmMacOsWidgetNowPlayingStore()

        private fun resolveDefaultGroupContainerDirectory(): Path? {
            val home = System.getProperty("user.home")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return Path.of(home, "Library", "Group Containers", GROUP_CONTAINER_ID)
        }
    }

    private val snapshotFile: Path?
        get() = groupContainerDirectory?.resolve(SNAPSHOT_RELATIVE_PATH)

    private val commandFile: Path?
        get() = groupContainerDirectory?.resolve(COMMAND_RELATIVE_PATH)

    override fun update(payload: JvmNowPlayingPayload) {
        val file = snapshotFile ?: return
        synchronized(snapshotLock) {
            val previousSnapshot = readSnapshot(file)?.takeIf { it.title == payload.title }
            val artworkPath = copyArtworkIntoSharedContainer(payload.artworkPath) ?: previousSnapshot?.artworkPath
            val lyricsText = payload.lyricsText.cleanWidgetLyricsText() ?: previousSnapshot?.lyricsText.cleanWidgetLyricsText()
            snapshotWriter(
                file,
                buildString {
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
                    append(",\"lyricsText\":")
                    appendJsonNullableString(lyricsText)
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
                    append(clockEpochSeconds())
                    append("}")
                },
            )
        }
    }

    override fun clear() {
        val file = snapshotFile ?: return
        synchronized(snapshotLock) {
            snapshotWriter(file, """{"hasTrack":false,"updatedAtEpochSeconds":${clockEpochSeconds()}}""")
        }
    }

    override fun updateLyrics(text: String?) {
        val file = snapshotFile ?: return
        synchronized(snapshotLock) {
            runCatching {
                val json = Files.readString(file)
                if (!json.contains(""""hasTrack":true""")) return@runCatching
                snapshotWriter(
                    file,
                    json
                        .upsertJsonNullableString("lyricsText", text.cleanWidgetLyricsText())
                        .upsertJsonLong("updatedAtEpochSeconds", clockEpochSeconds()),
                )
            }
        }
    }

    override fun consumeCommand(): MacOsNowPlayingCommand? {
        val file = commandFile ?: return null
        return runCatching {
            if (!Files.isRegularFile(file)) return null
            val json = Files.readString(file)
            file.deleteIfExists()
            when (json.readJsonString("command")) {
                "play" -> MacOsNowPlayingCommand.Play
                "pause" -> MacOsNowPlayingCommand.Pause
                "togglePlayPause" -> MacOsNowPlayingCommand.TogglePlayPause
                "next" -> MacOsNowPlayingCommand.Next
                "previous" -> MacOsNowPlayingCommand.Previous
                else -> null
            }
        }.getOrNull()
    }

    private fun copyArtworkIntoSharedContainer(artworkPath: String?): String? {
        val containerDirectory = groupContainerDirectory ?: return null
        val sourcePath = artworkPath
            ?.trim()
            ?.takeIf { it.startsWith("/") }
            ?.let { runCatching { Path.of(it) }.getOrNull() }
            ?.takeIf { Files.isRegularFile(it) }
            ?: return null
        val target = containerDirectory.resolve(ARTWORK_RELATIVE_PATH)
        return runCatching {
            target.parent?.createDirectories()
            val temp = Files.createTempFile(target.parent, "current-artwork-", ".tmp")
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

    private fun readSnapshot(file: Path): ExistingWidgetSnapshot? {
        return runCatching {
            val json = Files.readString(file)
            ExistingWidgetSnapshot(
                title = json.readJsonString("title") ?: return null,
                artworkPath = json.readJsonString("artworkPath"),
                lyricsText = json.readJsonString("lyricsText"),
            )
        }.getOrNull()
    }
}

@OptIn(ExperimentalPathApi::class)
private fun writeWidgetSnapshot(file: Path, json: String) {
    runCatching {
        file.parent?.createDirectories()
        val temp = Files.createTempFile(file.parent, "now-playing-", ".tmp")
        try {
            temp.outputStream().bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(json.ifBlank { EMPTY_WIDGET_SNAPSHOT })
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

private const val EMPTY_WIDGET_SNAPSHOT = """{"hasTrack":false,"updatedAtEpochSeconds":0}"""

private data class ExistingWidgetSnapshot(
    val title: String,
    val artworkPath: String?,
    val lyricsText: String?,
)

private fun String?.cleanWidgetLyricsText(): String? {
    if (this == null) return null
    val lines = lineSequence()
        .map { it.cleanWidgetLyricLine() }
        .take(3)
        .toList()
    return lines
        .joinToString(separator = "\n")
        .takeIf { lines.any { it.isNotBlank() } }
}

private fun String.cleanWidgetLyricLine(): String {
    var text = trim()
    while (text.startsWith("[")) {
        val end = text.indexOf(']')
        if (end < 0) break
        text = text.substring(end + 1).trim()
    }
    return text
}

private fun String.readJsonString(key: String): String? {
    val quotedKey = "\"${key.jsonEscaped()}\""
    var index = indexOf(quotedKey)
    if (index < 0) return null
    index += quotedKey.length
    index = skipJsonWhitespace(index)
    if (getOrNull(index) != ':') return null
    index = skipJsonWhitespace(index + 1)
    if (getOrNull(index) != '"') return null
    return readJsonQuotedString(index + 1)
}

private fun String.skipJsonWhitespace(startIndex: Int): Int {
    var index = startIndex
    while (index < length && this[index].isWhitespace()) {
        index += 1
    }
    return index
}

private fun String.readJsonQuotedString(startIndex: Int): String? {
    val result = StringBuilder()
    var index = startIndex
    while (index < length) {
        when (val char = this[index]) {
            '"' -> return result.toString()
            '\\' -> {
                index += 1
                if (index >= length) return null
                when (val escaped = this[index]) {
                    '"', '\\', '/' -> result.append(escaped)
                    'b' -> result.append('\b')
                    'f' -> result.append('\u000C')
                    'n' -> result.append('\n')
                    'r' -> result.append('\r')
                    't' -> result.append('\t')
                    'u' -> {
                        if (index + 4 >= length) return null
                        val code = substring(index + 1, index + 5).toIntOrNull(radix = 16) ?: return null
                        result.append(code.toChar())
                        index += 4
                    }
                    else -> return null
                }
            }
            else -> result.append(char)
        }
        index += 1
    }
    return null
}

private fun String.upsertJsonNullableString(key: String, value: String?): String {
    return upsertJsonValue(key, buildString { appendJsonNullableString(value) })
}

private fun String.upsertJsonLong(key: String, value: Long): String {
    return upsertJsonValue(key, value.toString())
}

private fun String.upsertJsonValue(key: String, encodedValue: String): String {
    val quotedKey = "\"${key.jsonEscaped()}\""
    val keyIndex = indexOf(quotedKey)
    if (keyIndex >= 0) {
        var colonIndex = keyIndex + quotedKey.length
        colonIndex = skipJsonWhitespace(colonIndex)
        if (getOrNull(colonIndex) != ':') return this
        val valueStart = skipJsonWhitespace(colonIndex + 1)
        val valueEnd = findJsonValueEnd(valueStart) ?: return this
        return replaceRange(valueStart, valueEnd, encodedValue)
    }
    val insertIndex = lastIndexOf('}')
    if (insertIndex < 0) return this
    val needsComma = substring(0, insertIndex).trimEnd().lastOrNull() != '{'
    val insertion = buildString {
        if (needsComma) append(',')
        append(quotedKey)
        append(':')
        append(encodedValue)
    }
    return replaceRange(insertIndex, insertIndex, insertion)
}

private fun String.findJsonValueEnd(startIndex: Int): Int? {
    return when (getOrNull(startIndex)) {
        '"' -> {
            var index = startIndex + 1
            while (index < length) {
                when (this[index]) {
                    '"' -> return index + 1
                    '\\' -> index += 1
                }
                index += 1
            }
            null
        }
        else -> {
            var index = startIndex
            while (index < length && this[index] != ',' && this[index] != '}') {
                index += 1
            }
            index.takeIf { it > startIndex }
        }
    }
}

private fun String.jsonEscaped(): String {
    return buildString {
        this@jsonEscaped.forEach { char ->
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
