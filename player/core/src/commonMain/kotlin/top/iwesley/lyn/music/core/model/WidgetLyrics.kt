package top.iwesley.lyn.music.core.model

interface WidgetLyricsPlatformService {
    val isSupported: Boolean

    suspend fun updateLyrics(text: String?)
}

object UnsupportedWidgetLyricsPlatformService : WidgetLyricsPlatformService {
    override val isSupported: Boolean = false

    override suspend fun updateLyrics(text: String?) = Unit
}
