package top.iwesley.lyn.music.platform

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.core.model.ArtworkCacheStore
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.NoopDiagnosticLogger
import top.iwesley.lyn.music.core.model.PlaybackSnapshot
import top.iwesley.lyn.music.core.model.SystemPlaybackControlCallbacks
import top.iwesley.lyn.music.core.model.SystemPlaybackControlsPlatformService
import top.iwesley.lyn.music.core.model.UnsupportedSystemPlaybackControlsPlatformService
import top.iwesley.lyn.music.core.model.trackArtworkCacheKey
import top.iwesley.lyn.music.core.model.warn

internal data class JvmSystemPlaybackControlsPlatformServiceRegistration(
    val service: SystemPlaybackControlsPlatformService,
    val isSupported: Boolean,
)

internal fun createJvmSystemPlaybackControlsPlatformService(
    logger: DiagnosticLogger = NoopDiagnosticLogger,
    artworkCacheStore: ArtworkCacheStore = createJvmArtworkCacheStore(),
    osName: String = System.getProperty("os.name").orEmpty(),
    bridgeLoader: () -> MacOsNowPlayingBridge = { JnaMacOsNowPlayingBridge.load() },
): JvmSystemPlaybackControlsPlatformServiceRegistration {
    if (!isMacOsName(osName)) {
        return JvmSystemPlaybackControlsPlatformServiceRegistration(
            service = UnsupportedSystemPlaybackControlsPlatformService,
            isSupported = false,
        )
    }
    val bridge = runCatching { bridgeLoader() }
        .onFailure { throwable ->
            logger.warn(JVM_SYSTEM_PLAYBACK_CONTROLS_TAG) {
                "macOS now playing bridge unavailable: ${throwable.message.orEmpty()}"
            }
        }
        .getOrNull()
        ?: return JvmSystemPlaybackControlsPlatformServiceRegistration(
            service = UnsupportedSystemPlaybackControlsPlatformService,
            isSupported = false,
        )
    return JvmSystemPlaybackControlsPlatformServiceRegistration(
        service = JvmSystemPlaybackControlsPlatformService(
            bridge = bridge,
            artworkCacheStore = artworkCacheStore,
        ),
        isSupported = true,
    )
}

internal data class JvmNowPlayingPayload(
    val title: String,
    val artist: String?,
    val album: String?,
    val artworkPath: String?,
    val lyricsText: String? = null,
    val durationMs: Long,
    val positionMs: Long,
    val isPlaying: Boolean,
    val canSeek: Boolean,
    val hasNext: Boolean,
    val hasPrevious: Boolean,
)

internal fun buildJvmNowPlayingPayload(
    snapshot: PlaybackSnapshot,
    artworkPath: String?,
): JvmNowPlayingPayload? {
    val track = snapshot.currentTrack ?: return null
    val durationMs = maxOf(snapshot.durationMs, track.durationMs, 0L)
    val positionMs = snapshot.positionMs.coerceAtLeast(0L)
    val hasQueueNavigation = snapshot.queue.size > 1
    return JvmNowPlayingPayload(
        title = snapshot.currentDisplayTitle.ifBlank { track.title }.ifBlank { "LeonMusic" },
        artist = snapshot.currentDisplayArtistName?.trim()?.takeIf { it.isNotBlank() },
        album = snapshot.currentDisplayAlbumTitle?.trim()?.takeIf { it.isNotBlank() },
        artworkPath = artworkPath?.trim()?.takeIf { it.isNotBlank() },
        durationMs = durationMs,
        positionMs = if (durationMs > 0L) positionMs.coerceAtMost(durationMs) else positionMs,
        isPlaying = snapshot.isPlaying,
        canSeek = snapshot.canSeek,
        hasNext = hasQueueNavigation,
        hasPrevious = hasQueueNavigation,
    )
}

internal class JvmSystemPlaybackControlsPlatformService(
    private val bridge: MacOsNowPlayingBridge,
    private val artworkCacheStore: ArtworkCacheStore = createJvmArtworkCacheStore(),
    private val widgetNowPlayingWriter: JvmMacOsWidgetNowPlayingWriter = JvmMacOsWidgetNowPlayingStore.default(),
    private val widgetPlaybackCommandReader: JvmMacOsWidgetPlaybackCommandReader = JvmMacOsWidgetNowPlayingStore.default(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : SystemPlaybackControlsPlatformService {
    private var callbacks = SystemPlaybackControlCallbacks()
    private var latestArtworkLocator: String? = null
    private var latestArtworkCacheKey: String? = null
    private var latestArtworkPath: String? = null

    init {
        bridge.setCommandHandler { command ->
            dispatchCommand(command)
        }
        scope.launch {
            while (true) {
                widgetPlaybackCommandReader.consumeCommand()?.let(::dispatchCommand)
                delay(JVM_WIDGET_COMMAND_POLL_INTERVAL_MILLIS)
            }
        }
    }

    override fun bind(callbacks: SystemPlaybackControlCallbacks) {
        this.callbacks = callbacks
    }

    override suspend fun updateSnapshot(snapshot: PlaybackSnapshot) {
        val currentTrack = snapshot.currentTrack
        if (currentTrack == null) {
            latestArtworkLocator = null
            latestArtworkCacheKey = null
            latestArtworkPath = null
            widgetNowPlayingWriter.clear()
            bridge.clear()
            return
        }
        val artworkPath = resolveArtworkPath(snapshot)
        val payload = buildJvmNowPlayingPayload(snapshot, artworkPath)
        if (payload == null) {
            widgetNowPlayingWriter.clear()
            bridge.clear()
            return
        }
        widgetNowPlayingWriter.update(payload)
        bridge.update(payload)
    }

    override suspend fun close() {
        widgetNowPlayingWriter.clear()
        bridge.clear()
        bridge.dispose()
        scope.cancel()
    }

    private suspend fun resolveArtworkPath(snapshot: PlaybackSnapshot): String? {
        val normalized = snapshot.currentDisplayArtworkLocator?.trim()?.takeIf { it.isNotBlank() }
        val cacheKey = snapshot.currentTrack
            ?.let(::trackArtworkCacheKey)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: normalized
        if (normalized == latestArtworkLocator && cacheKey == latestArtworkCacheKey) return latestArtworkPath
        latestArtworkLocator = normalized
        latestArtworkCacheKey = cacheKey
        latestArtworkPath = cacheKey
            ?.let(artworkCacheStore::peekCachedTarget)
            ?.target
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: normalized?.let { artworkCacheStore.cache(it, cacheKey ?: it) }
        return latestArtworkPath
    }

    private fun dispatchCommand(command: MacOsNowPlayingCommand) {
        when (command) {
            is MacOsNowPlayingCommand.Play -> scope.launch { callbacks.play() }
            is MacOsNowPlayingCommand.Pause -> scope.launch { callbacks.pause() }
            is MacOsNowPlayingCommand.TogglePlayPause -> scope.launch { callbacks.togglePlayPause() }
            is MacOsNowPlayingCommand.Next -> scope.launch { callbacks.skipNext() }
            is MacOsNowPlayingCommand.Previous -> scope.launch { callbacks.skipPrevious() }
            is MacOsNowPlayingCommand.Seek -> scope.launch { callbacks.seekTo(command.positionMs) }
        }
    }
}

internal sealed interface MacOsNowPlayingCommand {
    data object Play : MacOsNowPlayingCommand
    data object Pause : MacOsNowPlayingCommand
    data object TogglePlayPause : MacOsNowPlayingCommand
    data object Next : MacOsNowPlayingCommand
    data object Previous : MacOsNowPlayingCommand
    data class Seek(val positionMs: Long) : MacOsNowPlayingCommand
}

internal interface MacOsNowPlayingBridge {
    fun setCommandHandler(handler: (MacOsNowPlayingCommand) -> Unit)
    fun update(payload: JvmNowPlayingPayload)
    fun clear()
    fun dispose()
}

private class JnaMacOsNowPlayingBridge private constructor(
    private val nativeLibrary: LeonMusicNowPlayingNativeLibrary,
    private val handle: Pointer,
    @Suppress("unused")
    private val nativeCallback: LeonMusicNowPlayingCommandCallback,
    private val commandHandlerRef: AtomicReference<(MacOsNowPlayingCommand) -> Unit>,
) : MacOsNowPlayingBridge {
    private var isDisposed = false

    override fun setCommandHandler(handler: (MacOsNowPlayingCommand) -> Unit) {
        commandHandlerRef.set(handler)
    }

    override fun update(payload: JvmNowPlayingPayload) {
        if (isDisposed) return
        nativeLibrary.lyn_music_now_playing_update(
            handle,
            payload.title,
            payload.artist,
            payload.album,
            payload.artworkPath,
            payload.durationMs,
            payload.positionMs,
            payload.isPlaying.toNativeInt(),
            payload.canSeek.toNativeInt(),
            payload.hasNext.toNativeInt(),
            payload.hasPrevious.toNativeInt(),
        )
    }

    override fun clear() {
        if (isDisposed) return
        nativeLibrary.lyn_music_now_playing_clear(handle)
    }

    override fun dispose() {
        if (isDisposed) return
        isDisposed = true
        nativeLibrary.lyn_music_now_playing_dispose(handle)
    }

    companion object {
        fun load(): JnaMacOsNowPlayingBridge {
            val nativeLibrary = MacOsNowPlayingNativeLibraryProvider.nativeLibrary
            val commandHandlerRef = AtomicReference<(MacOsNowPlayingCommand) -> Unit>({})
            val callback = LeonMusicNowPlayingCommandCallback { command, value ->
                decodeMacOsNowPlayingCommand(command, value)?.let { decoded ->
                    commandHandlerRef.get().invoke(decoded)
                }
            }
            val handle = nativeLibrary.lyn_music_now_playing_create(callback)
                ?: error("macOS now playing bridge returned a null handle")
            return JnaMacOsNowPlayingBridge(nativeLibrary, handle, callback, commandHandlerRef)
        }
    }
}

private fun decodeMacOsNowPlayingCommand(command: Int, value: Double): MacOsNowPlayingCommand? {
    return when (command) {
        NATIVE_COMMAND_PLAY -> MacOsNowPlayingCommand.Play
        NATIVE_COMMAND_PAUSE -> MacOsNowPlayingCommand.Pause
        NATIVE_COMMAND_TOGGLE_PLAY_PAUSE -> MacOsNowPlayingCommand.TogglePlayPause
        NATIVE_COMMAND_NEXT -> MacOsNowPlayingCommand.Next
        NATIVE_COMMAND_PREVIOUS -> MacOsNowPlayingCommand.Previous
        NATIVE_COMMAND_SEEK -> MacOsNowPlayingCommand.Seek((value * 1_000.0).toLong().coerceAtLeast(0L))
        else -> null
    }
}

private interface LeonMusicNowPlayingNativeLibrary : Library {
    fun lyn_music_now_playing_create(callback: LeonMusicNowPlayingCommandCallback): Pointer?

    fun lyn_music_now_playing_update(
        handle: Pointer,
        title: String,
        artist: String?,
        album: String?,
        artworkPath: String?,
        durationMs: Long,
        positionMs: Long,
        isPlaying: Int,
        canSeek: Int,
        hasNext: Int,
        hasPrevious: Int,
    ): Int

    fun lyn_music_now_playing_clear(handle: Pointer): Int

    fun lyn_music_now_playing_dispose(handle: Pointer): Int

    fun lyn_music_widget_reload_timelines(): Int
}

private object MacOsNowPlayingNativeLibraryProvider {
    val nativeLibrary: LeonMusicNowPlayingNativeLibrary by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        Native.load(
            macOsNowPlayingBridgeLibraryPath().toAbsolutePath().toString(),
            LeonMusicNowPlayingNativeLibrary::class.java,
            mapOf(Library.OPTION_STRING_ENCODING to Charsets.UTF_8.name()),
        )
    }
}

private fun interface LeonMusicNowPlayingCommandCallback : Callback {
    fun invoke(command: Int, value: Double)
}

internal fun reloadJvmMacOsWidgetTimeline(
    logger: DiagnosticLogger = NoopDiagnosticLogger,
): Boolean {
    return runCatching {
        MacOsNowPlayingNativeLibraryProvider.nativeLibrary.lyn_music_widget_reload_timelines() == 1
    }.onFailure { throwable ->
        logger.warn(JVM_SYSTEM_PLAYBACK_CONTROLS_TAG) {
            "macOS widget timeline reload unavailable: ${throwable.message.orEmpty()}"
        }
    }.getOrDefault(false)
}

private fun Boolean.toNativeInt(): Int = if (this) 1 else 0

private fun isMacOsName(osName: String): Boolean = osName.contains("mac", ignoreCase = true)

private const val JVM_SYSTEM_PLAYBACK_CONTROLS_TAG = "JvmSystemPlaybackControls"
private const val NATIVE_COMMAND_PLAY = 1
private const val NATIVE_COMMAND_PAUSE = 2
private const val NATIVE_COMMAND_TOGGLE_PLAY_PAUSE = 3
private const val NATIVE_COMMAND_NEXT = 4
private const val NATIVE_COMMAND_PREVIOUS = 5
private const val NATIVE_COMMAND_SEEK = 6
private const val JVM_WIDGET_COMMAND_POLL_INTERVAL_MILLIS = 350L
