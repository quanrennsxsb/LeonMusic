package top.iwesley.lyn.music.feature.player

import top.iwesley.lyn.music.core.model.LyricsDocument

const val DESKTOP_LYRICS_LOADING_TEXT: String = "正在准备歌词"

fun findDesktopLyricsHighlightedLine(
    lyrics: LyricsDocument?,
    positionMs: Long,
): Int {
    val lines = lyrics?.lines ?: return -1
    val target = positionMs + lyrics.offsetMs
    return lines.indexOfLast { line ->
        line.timestampMs?.let { it <= target } ?: false
    }
}

fun resolveDesktopLyricsOverlayText(
    lyrics: LyricsDocument?,
    highlightedLineIndex: Int,
    isLyricsLoading: Boolean,
): String? {
    val lines = lyrics?.lines.orEmpty()
    fun lineTextAt(index: Int): String? = lines.widgetLyricTextAt(index)
    lineTextAt(highlightedLineIndex)?.let { return it }
    for (index in highlightedLineIndex + 1 until lines.size) {
        lineTextAt(index)?.let { return it }
    }
    for (index in highlightedLineIndex - 1 downTo 0) {
        lineTextAt(index)?.let { return it }
    }
    return if (isLyricsLoading) DESKTOP_LYRICS_LOADING_TEXT else null
}

/** Returns the current visible lyric line for the desktop widget. */
fun resolveWidgetLyricsText(
    lyrics: LyricsDocument?,
    highlightedLineIndex: Int,
): String? {
    val lines = lyrics?.lines.orEmpty()
    val currentIndex = sequence {
        yield(highlightedLineIndex)
        yieldAll((highlightedLineIndex + 1 until lines.size))
        yieldAll((highlightedLineIndex - 1 downTo 0))
    }.firstOrNull { index -> lines.widgetLyricTextAt(index) != null } ?: return null
    return lines.widgetLyricTextAt(currentIndex)
}

private fun List<top.iwesley.lyn.music.core.model.LyricsLine>.widgetLyricTextAt(index: Int): String? {
    return getOrNull(index)
        ?.text
        ?.trim()
        ?.takeIf { text ->
            text.isNotEmpty() &&
                !isPlayerLyricsStructureTagLine(text) &&
                !isPlayerLyricsStatusLine(text)
        }
}

private fun isPlayerLyricsStatusLine(text: String): Boolean {
    return PLAYER_LYRICS_STATUS_LINES.any { status -> text == status }
}

private val PLAYER_LYRICS_STATUS_LINES = setOf(
    "获取歌词信息失败",
    "获取歌词失败",
    "歌词获取失败",
)
