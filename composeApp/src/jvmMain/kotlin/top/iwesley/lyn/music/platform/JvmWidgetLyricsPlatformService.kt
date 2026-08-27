package top.iwesley.lyn.music.platform

import top.iwesley.lyn.music.core.model.WidgetLyricsPlatformService

internal class JvmWidgetLyricsPlatformService(
    private val store: JvmMacOsWidgetLyricsWriter = JvmMacOsWidgetNowPlayingStore.default(),
    private val reloadWidgetTimeline: () -> Unit = { reloadJvmMacOsWidgetTimeline() },
    osName: String = System.getProperty("os.name").orEmpty(),
) : WidgetLyricsPlatformService {
    override val isSupported: Boolean = osName.contains("mac", ignoreCase = true)
    private var latestLyrics: String? = null
    private var hasPublishedLyrics = false

    override suspend fun updateLyrics(text: String?) {
        if (!isSupported) return
        val normalizedText = text?.trim()?.takeIf { it.isNotEmpty() }
        if (hasPublishedLyrics && latestLyrics == normalizedText) return
        latestLyrics = normalizedText
        hasPublishedLyrics = true
        store.updateLyrics(normalizedText)
        reloadWidgetTimeline()
    }
}
