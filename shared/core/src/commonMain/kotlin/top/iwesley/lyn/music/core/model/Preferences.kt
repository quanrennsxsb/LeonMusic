package top.iwesley.lyn.music.core.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class AppDisplayScalePreset(
    val scale: Float,
) {
    Compact(0.9f),
    Default(1.0f),
    Large(1.1f),
}

enum class NavidromeAudioQuality(
    val maxBitRateKbps: Int?,
) {
    Original(null),
    Kbps320(320),
    Kbps192(192),
    Kbps128(128),
}

enum class NavidromePlaybackCacheSizePreset(
    val sizeBytes: Long,
) {
    GB1(1L * 1024L * 1024L * 1024L),
    GB2(2L * 1024L * 1024L * 1024L),
    GB5(5L * 1024L * 1024L * 1024L),
}

enum class PlayerArtworkStyle {
    VINYL,
    HALF_RECORD,
    MINIMAL_COVER,
}

enum class PlayerVisualSizePreset(
    val scale: Float,
) {
    Small(0.86f),
    Standard(1.0f),
    Large(1.14f),
    VeryLarge(1.28f),
    Maximum(1.42f),
}

val DEFAULT_NAVIDROME_WIFI_AUDIO_QUALITY: NavidromeAudioQuality = NavidromeAudioQuality.Original
val DEFAULT_NAVIDROME_MOBILE_AUDIO_QUALITY: NavidromeAudioQuality = NavidromeAudioQuality.Kbps192
val DEFAULT_NAVIDROME_PLAYBACK_CACHE_SIZE_PRESET: NavidromePlaybackCacheSizePreset =
    NavidromePlaybackCacheSizePreset.GB2
const val DEFAULT_NAVIDROME_PLAYBACK_CACHE_ENABLED: Boolean = true
const val DEFAULT_ANDROID_EXTENSION_DECODER_ENABLED: Boolean = false
const val DEFAULT_MINIMIZE_WINDOW_ON_CLOSE: Boolean = true
val DEFAULT_PLAYER_ARTWORK_STYLE: PlayerArtworkStyle = PlayerArtworkStyle.VINYL
val DEFAULT_PLAYER_LYRICS_FONT_SIZE_PRESET: PlayerVisualSizePreset = PlayerVisualSizePreset.Standard
val DEFAULT_PLAYER_ARTWORK_SIZE_PRESET: PlayerVisualSizePreset = PlayerVisualSizePreset.Standard
const val DEFAULT_AUTO_PLAY_ON_STARTUP_DELAY_SECONDS: Int = 5
const val MAX_AUTO_PLAY_ON_STARTUP_DELAY_SECONDS: Int = 30

fun appDisplayScalePresetOrDefault(name: String?): AppDisplayScalePreset {
    return AppDisplayScalePreset.entries.firstOrNull { it.name == name } ?: AppDisplayScalePreset.Default
}

fun navidromeAudioQualityOrDefault(
    name: String?,
    default: NavidromeAudioQuality,
): NavidromeAudioQuality {
    return NavidromeAudioQuality.entries.firstOrNull { it.name == name } ?: default
}

fun navidromePlaybackCacheSizePresetOrDefault(name: String?): NavidromePlaybackCacheSizePreset {
    return NavidromePlaybackCacheSizePreset.entries.firstOrNull { it.name == name }
        ?: DEFAULT_NAVIDROME_PLAYBACK_CACHE_SIZE_PRESET
}

fun playerArtworkStyleOrDefault(name: String?): PlayerArtworkStyle {
    return PlayerArtworkStyle.entries.firstOrNull { it.name == name } ?: DEFAULT_PLAYER_ARTWORK_STYLE
}

fun playerVisualSizePresetOrDefault(
    name: String?,
    default: PlayerVisualSizePreset = PlayerVisualSizePreset.Standard,
): PlayerVisualSizePreset {
    return PlayerVisualSizePreset.entries.firstOrNull { it.name == name } ?: default
}

fun normalizeAutoPlayOnStartupDelaySeconds(value: Int?): Int {
    return (value ?: DEFAULT_AUTO_PLAY_ON_STARTUP_DELAY_SECONDS)
        .coerceIn(0, MAX_AUTO_PLAY_ON_STARTUP_DELAY_SECONDS)
}

fun effectiveAppDisplayDensity(
    baseDensity: Float,
    preset: AppDisplayScalePreset,
): Float {
    if (!baseDensity.isFinite() || baseDensity <= 0f) return baseDensity
    return baseDensity * preset.scale
}

interface SambaCachePreferencesStore {
    val useSambaCache: StateFlow<Boolean>

    suspend fun setUseSambaCache(enabled: Boolean)
}

interface ThemePreferencesStore {
    val selectedTheme: StateFlow<AppThemeId>
    val customThemeTokens: StateFlow<AppThemeTokens>
    val textPalettePreferences: StateFlow<AppThemeTextPalettePreferences>

    suspend fun setSelectedTheme(themeId: AppThemeId)
    suspend fun setCustomThemeTokens(tokens: AppThemeTokens)
    suspend fun setTextPalette(themeId: AppThemeId, palette: AppThemeTextPalette)
}

interface CompactPlayerLyricsPreferencesStore {
    val showCompactPlayerLyrics: StateFlow<Boolean>

    suspend fun setShowCompactPlayerLyrics(enabled: Boolean)
}

interface DesktopLyricsPreferencesStore {
    val showDesktopLyrics: StateFlow<Boolean>

    suspend fun setShowDesktopLyrics(enabled: Boolean)
}

interface MenuBarLyricsControlsPreferencesStore {
    val showMenuBarLyricsControls: StateFlow<Boolean>

    suspend fun setShowMenuBarLyricsControls(enabled: Boolean)
}

interface AutoPlayOnStartupPreferencesStore {
    val autoPlayOnStartup: StateFlow<Boolean>
    val autoPlayOnStartupDelaySeconds: StateFlow<Int>

    suspend fun setAutoPlayOnStartup(enabled: Boolean)
    suspend fun setAutoPlayOnStartupDelaySeconds(seconds: Int)
}

interface AutoOpenPlayerOnStartupPreferencesStore {
    val autoOpenPlayerOnStartup: StateFlow<Boolean>

    suspend fun setAutoOpenPlayerOnStartup(enabled: Boolean)
}

interface WindowClosePreferencesStore {
    val minimizeWindowOnClose: StateFlow<Boolean>

    suspend fun setMinimizeWindowOnClose(enabled: Boolean)
}

interface AppDisplayPreferencesStore {
    val appDisplayScalePreset: StateFlow<AppDisplayScalePreset>

    suspend fun setAppDisplayScalePreset(preset: AppDisplayScalePreset)
}

interface NavidromeAudioQualityPreferencesStore {
    val navidromeWifiAudioQuality: StateFlow<NavidromeAudioQuality>
    val navidromeMobileAudioQuality: StateFlow<NavidromeAudioQuality>

    suspend fun setNavidromeWifiAudioQuality(quality: NavidromeAudioQuality)
    suspend fun setNavidromeMobileAudioQuality(quality: NavidromeAudioQuality)
}

interface NavidromePlaybackCachePreferencesStore {
    val navidromePlaybackCacheEnabled: StateFlow<Boolean>
    val navidromePlaybackCacheDirectory: StateFlow<LocalFolderSelection?>
    val navidromePlaybackCacheSizePreset: StateFlow<NavidromePlaybackCacheSizePreset>

    suspend fun setNavidromePlaybackCacheEnabled(enabled: Boolean)
    suspend fun setNavidromePlaybackCacheDirectory(selection: LocalFolderSelection?)
    suspend fun setNavidromePlaybackCacheSizePreset(preset: NavidromePlaybackCacheSizePreset)
}

interface PlaybackDecoderPreferencesStore {
    val useAndroidExtensionDecoder: StateFlow<Boolean>

    suspend fun setUseAndroidExtensionDecoder(enabled: Boolean)
}

interface PlayerArtworkStylePreferencesStore {
    val playerArtworkStyle: StateFlow<PlayerArtworkStyle>

    suspend fun setPlayerArtworkStyle(style: PlayerArtworkStyle)
}

interface PlayerLyricsFontSizePreferencesStore {
    val playerLyricsFontSizePreset: StateFlow<PlayerVisualSizePreset>

    suspend fun setPlayerLyricsFontSizePreset(preset: PlayerVisualSizePreset)
}

interface PlayerLyricsColorPreferencesStore {
    val playerLyricsColorPreference: StateFlow<PlayerLyricsColorPreference>
    val playerActiveLyricsColorPreference: StateFlow<PlayerLyricsColorPreference>

    suspend fun setPlayerLyricsColorPreference(preference: PlayerLyricsColorPreference)
    suspend fun setPlayerActiveLyricsColorPreference(preference: PlayerLyricsColorPreference)
}

interface PlayerArtworkSizePreferencesStore {
    val playerArtworkSizePreset: StateFlow<PlayerVisualSizePreset>

    suspend fun setPlayerArtworkSizePreset(preset: PlayerVisualSizePreset)
}

interface LyricsShareFontPreferencesStore {
    val selectedLyricsShareFontKey: StateFlow<String?>

    suspend fun setSelectedLyricsShareFontKey(value: String?)
}

object UnsupportedCompactPlayerLyricsPreferencesStore : CompactPlayerLyricsPreferencesStore {
    private val mutableShowCompactPlayerLyrics = MutableStateFlow(false)

    override val showCompactPlayerLyrics: StateFlow<Boolean> = mutableShowCompactPlayerLyrics

    override suspend fun setShowCompactPlayerLyrics(enabled: Boolean) {
        mutableShowCompactPlayerLyrics.value = enabled
    }
}

object UnsupportedDesktopLyricsPreferencesStore : DesktopLyricsPreferencesStore {
    private val mutableShowDesktopLyrics = MutableStateFlow(false)

    override val showDesktopLyrics: StateFlow<Boolean> = mutableShowDesktopLyrics

    override suspend fun setShowDesktopLyrics(enabled: Boolean) {
        mutableShowDesktopLyrics.value = enabled
    }
}

object UnsupportedMenuBarLyricsControlsPreferencesStore : MenuBarLyricsControlsPreferencesStore {
    private val mutableShowMenuBarLyricsControls = MutableStateFlow(false)

    override val showMenuBarLyricsControls: StateFlow<Boolean> = mutableShowMenuBarLyricsControls

    override suspend fun setShowMenuBarLyricsControls(enabled: Boolean) {
        mutableShowMenuBarLyricsControls.value = enabled
    }
}

object UnsupportedAutoPlayOnStartupPreferencesStore : AutoPlayOnStartupPreferencesStore {
    private val mutableAutoPlayOnStartup = MutableStateFlow(false)
    private val mutableAutoPlayOnStartupDelaySeconds = MutableStateFlow(DEFAULT_AUTO_PLAY_ON_STARTUP_DELAY_SECONDS)

    override val autoPlayOnStartup: StateFlow<Boolean> = mutableAutoPlayOnStartup
    override val autoPlayOnStartupDelaySeconds: StateFlow<Int> = mutableAutoPlayOnStartupDelaySeconds

    override suspend fun setAutoPlayOnStartup(enabled: Boolean) {
        mutableAutoPlayOnStartup.value = enabled
    }

    override suspend fun setAutoPlayOnStartupDelaySeconds(seconds: Int) {
        mutableAutoPlayOnStartupDelaySeconds.value = normalizeAutoPlayOnStartupDelaySeconds(seconds)
    }
}

object UnsupportedAutoOpenPlayerOnStartupPreferencesStore : AutoOpenPlayerOnStartupPreferencesStore {
    private val mutableAutoOpenPlayerOnStartup = MutableStateFlow(false)

    override val autoOpenPlayerOnStartup: StateFlow<Boolean> = mutableAutoOpenPlayerOnStartup

    override suspend fun setAutoOpenPlayerOnStartup(enabled: Boolean) {
        mutableAutoOpenPlayerOnStartup.value = enabled
    }
}

object UnsupportedWindowClosePreferencesStore : WindowClosePreferencesStore {
    private val mutableMinimizeWindowOnClose = MutableStateFlow(DEFAULT_MINIMIZE_WINDOW_ON_CLOSE)

    override val minimizeWindowOnClose: StateFlow<Boolean> = mutableMinimizeWindowOnClose

    override suspend fun setMinimizeWindowOnClose(enabled: Boolean) {
        mutableMinimizeWindowOnClose.value = enabled
    }
}

object UnsupportedAppDisplayPreferencesStore : AppDisplayPreferencesStore {
    private val mutableAppDisplayScalePreset = MutableStateFlow(AppDisplayScalePreset.Default)

    override val appDisplayScalePreset: StateFlow<AppDisplayScalePreset> = mutableAppDisplayScalePreset

    override suspend fun setAppDisplayScalePreset(preset: AppDisplayScalePreset) {
        mutableAppDisplayScalePreset.value = preset
    }
}

object UnsupportedNavidromeAudioQualityPreferencesStore : NavidromeAudioQualityPreferencesStore {
    private val mutableWifiAudioQuality = MutableStateFlow(DEFAULT_NAVIDROME_WIFI_AUDIO_QUALITY)
    private val mutableMobileAudioQuality = MutableStateFlow(DEFAULT_NAVIDROME_MOBILE_AUDIO_QUALITY)

    override val navidromeWifiAudioQuality: StateFlow<NavidromeAudioQuality> = mutableWifiAudioQuality
    override val navidromeMobileAudioQuality: StateFlow<NavidromeAudioQuality> = mutableMobileAudioQuality

    override suspend fun setNavidromeWifiAudioQuality(quality: NavidromeAudioQuality) {
        mutableWifiAudioQuality.value = quality
    }

    override suspend fun setNavidromeMobileAudioQuality(quality: NavidromeAudioQuality) {
        mutableMobileAudioQuality.value = quality
    }
}

object UnsupportedNavidromePlaybackCachePreferencesStore : NavidromePlaybackCachePreferencesStore {
    private val mutableNavidromePlaybackCacheEnabled =
        MutableStateFlow(DEFAULT_NAVIDROME_PLAYBACK_CACHE_ENABLED)
    private val mutableNavidromePlaybackCacheDirectory =
        MutableStateFlow<LocalFolderSelection?>(null)
    private val mutableNavidromePlaybackCacheSizePreset =
        MutableStateFlow(DEFAULT_NAVIDROME_PLAYBACK_CACHE_SIZE_PRESET)

    override val navidromePlaybackCacheEnabled: StateFlow<Boolean> =
        mutableNavidromePlaybackCacheEnabled
    override val navidromePlaybackCacheDirectory: StateFlow<LocalFolderSelection?> =
        mutableNavidromePlaybackCacheDirectory
    override val navidromePlaybackCacheSizePreset: StateFlow<NavidromePlaybackCacheSizePreset> =
        mutableNavidromePlaybackCacheSizePreset

    override suspend fun setNavidromePlaybackCacheEnabled(enabled: Boolean) {
        mutableNavidromePlaybackCacheEnabled.value = enabled
    }

    override suspend fun setNavidromePlaybackCacheDirectory(selection: LocalFolderSelection?) {
        mutableNavidromePlaybackCacheDirectory.value = selection
    }

    override suspend fun setNavidromePlaybackCacheSizePreset(preset: NavidromePlaybackCacheSizePreset) {
        mutableNavidromePlaybackCacheSizePreset.value = preset
    }
}

object UnsupportedPlaybackDecoderPreferencesStore : PlaybackDecoderPreferencesStore {
    private val mutableUseAndroidExtensionDecoder =
        MutableStateFlow(DEFAULT_ANDROID_EXTENSION_DECODER_ENABLED)

    override val useAndroidExtensionDecoder: StateFlow<Boolean> = mutableUseAndroidExtensionDecoder

    override suspend fun setUseAndroidExtensionDecoder(enabled: Boolean) {
        mutableUseAndroidExtensionDecoder.value = enabled
    }
}

object UnsupportedPlayerArtworkStylePreferencesStore : PlayerArtworkStylePreferencesStore {
    private val mutablePlayerArtworkStyle = MutableStateFlow(DEFAULT_PLAYER_ARTWORK_STYLE)

    override val playerArtworkStyle: StateFlow<PlayerArtworkStyle> = mutablePlayerArtworkStyle

    override suspend fun setPlayerArtworkStyle(style: PlayerArtworkStyle) {
        mutablePlayerArtworkStyle.value = style
    }
}

object UnsupportedPlayerLyricsFontSizePreferencesStore : PlayerLyricsFontSizePreferencesStore {
    private val mutablePlayerLyricsFontSizePreset = MutableStateFlow(DEFAULT_PLAYER_LYRICS_FONT_SIZE_PRESET)

    override val playerLyricsFontSizePreset: StateFlow<PlayerVisualSizePreset> = mutablePlayerLyricsFontSizePreset

    override suspend fun setPlayerLyricsFontSizePreset(preset: PlayerVisualSizePreset) {
        mutablePlayerLyricsFontSizePreset.value = preset
    }
}

object UnsupportedPlayerLyricsColorPreferencesStore : PlayerLyricsColorPreferencesStore {
    private val mutablePlayerLyricsColorPreference = MutableStateFlow(PlayerLyricsColorPreference.Artwork)
    private val mutablePlayerActiveLyricsColorPreference = MutableStateFlow(PlayerLyricsColorPreference.Artwork)

    override val playerLyricsColorPreference: StateFlow<PlayerLyricsColorPreference> =
        mutablePlayerLyricsColorPreference
    override val playerActiveLyricsColorPreference: StateFlow<PlayerLyricsColorPreference> =
        mutablePlayerActiveLyricsColorPreference

    override suspend fun setPlayerLyricsColorPreference(preference: PlayerLyricsColorPreference) {
        mutablePlayerLyricsColorPreference.value = preference
    }

    override suspend fun setPlayerActiveLyricsColorPreference(preference: PlayerLyricsColorPreference) {
        mutablePlayerActiveLyricsColorPreference.value = preference
    }
}

object UnsupportedPlayerArtworkSizePreferencesStore : PlayerArtworkSizePreferencesStore {
    private val mutablePlayerArtworkSizePreset = MutableStateFlow(DEFAULT_PLAYER_ARTWORK_SIZE_PRESET)

    override val playerArtworkSizePreset: StateFlow<PlayerVisualSizePreset> = mutablePlayerArtworkSizePreset

    override suspend fun setPlayerArtworkSizePreset(preset: PlayerVisualSizePreset) {
        mutablePlayerArtworkSizePreset.value = preset
    }
}

object UnsupportedLyricsShareFontPreferencesStore : LyricsShareFontPreferencesStore {
    private val mutableSelectedLyricsShareFontKey = MutableStateFlow<String?>(null)

    override val selectedLyricsShareFontKey: StateFlow<String?> = mutableSelectedLyricsShareFontKey

    override suspend fun setSelectedLyricsShareFontKey(value: String?) {
        mutableSelectedLyricsShareFontKey.value = value?.trim()?.takeIf { it.isNotBlank() }
    }
}

interface DesktopVlcPreferencesStore {
    val desktopVlcManualPath: StateFlow<String?>
    val desktopVlcAutoDetectedPath: StateFlow<String?>
    val desktopVlcEffectivePath: StateFlow<String?>

    suspend fun setDesktopVlcManualPath(path: String?)
    suspend fun setDesktopVlcAutoDetectedPath(path: String?)
}

object UnsupportedDesktopVlcPreferencesStore : DesktopVlcPreferencesStore {
    private val mutableManualPath = MutableStateFlow<String?>(null)
    private val mutableAutoDetectedPath = MutableStateFlow<String?>(null)
    private val mutableEffectivePath = MutableStateFlow<String?>(null)

    override val desktopVlcManualPath: StateFlow<String?> = mutableManualPath
    override val desktopVlcAutoDetectedPath: StateFlow<String?> = mutableAutoDetectedPath
    override val desktopVlcEffectivePath: StateFlow<String?> = mutableEffectivePath

    override suspend fun setDesktopVlcManualPath(path: String?) {
        mutableManualPath.value = path?.takeIf { it.isNotBlank() }
        mutableEffectivePath.value = mutableManualPath.value ?: mutableAutoDetectedPath.value
    }

    override suspend fun setDesktopVlcAutoDetectedPath(path: String?) {
        mutableAutoDetectedPath.value = path?.takeIf { it.isNotBlank() }
        mutableEffectivePath.value = mutableManualPath.value ?: mutableAutoDetectedPath.value
    }
}
