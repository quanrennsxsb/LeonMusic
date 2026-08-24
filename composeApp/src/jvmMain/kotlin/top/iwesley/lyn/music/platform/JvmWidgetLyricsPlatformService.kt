package top.iwesley.lyn.music.platform

import top.iwesley.lyn.music.core.model.WidgetLyricsPlatformService

internal class JvmWidgetLyricsPlatformService(
    private val store: JvmMacOsWidgetLyricsWriter = JvmMacOsWidgetNowPlayingStore.default(),
    private val reloadWidgetTimeline: () -> Unit = { reloadJvmMacOsWidgetTimeline() },
    osName: String = System.getProperty("os.name").orEmpty(),
) : WidgetLyricsPlatformService {
    override val isSupported: Boolean = osName.contains("mac", ignoreCase = true)

    override suspend fun updateLyrics(text: String?) {
        if (!isSupported) return
        store.updateLyrics(text)
        reloadWidgetTimeline()
    }
}
