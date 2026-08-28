package top.iwesley.lyn.music.automotive

import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.ANDROID_AUTOMOTIVE_PLATFORM_NAME
import kotlin.math.min
import top.iwesley.lyn.music.App
import top.iwesley.lyn.music.LeonMusicAppComponent
import top.iwesley.lyn.music.StartupAutoOpenGate
import top.iwesley.lyn.music.StartupDatabaseErrorScreen
import top.iwesley.lyn.music.buildPlayerAppComponent
import top.iwesley.lyn.music.core.model.AppDisplayScalePreset
import top.iwesley.lyn.music.feature.player.PlayerState
import top.iwesley.lyn.music.core.model.effectiveAppDisplayDensity
import top.iwesley.lyn.music.feature.player.PlayerIntent
import top.iwesley.lyn.music.feature.settings.SettingsIntent
import top.iwesley.lyn.music.platform.AndroidExternalAudioOpenSupport
import top.iwesley.lyn.music.platform.createAndroidRuntimeGraph
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val startupAutoOpenViewModel by viewModels<StartupAutoOpenViewModel>()
    private val wakeResumeViewModel by viewModels<AutomotiveWakeResumeViewModel>()
    private val externalAudioOpenScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var appComponent: LeonMusicAppComponent? = null
    private var pendingExternalAudioOpenIntent: Intent? = null
    private var externalAudioOpenJob: Job? = null
    private var externalAudioOpenRequestId = 0L
    private var wakeResumeJob: Job? = null
    private var backActionDialog: AlertDialog? = null
    private val screenStateReceiver = AutomotiveWakeScreenStateReceiver(
        onScreenTurnedOff = { rememberPlaybackForWake() },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        //enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    ContextCompat.registerReceiver(
                        this@MainActivity,
                        screenStateReceiver,
                        IntentFilter(Intent.ACTION_SCREEN_OFF),
                        ContextCompat.RECEIVER_NOT_EXPORTED,
                    )
                }

                override fun onStop(owner: LifecycleOwner) {
                    rememberPlaybackForWake()
                    runCatching { unregisterReceiver(screenStateReceiver) }
                }
            },
        )
        val appComponentResult = runCatching {
            val runtimeGraph = createAndroidRuntimeGraph(this, platformName = ANDROID_AUTOMOTIVE_PLATFORM_NAME)
            try {
                buildPlayerAppComponent(
                    sharedGraph = runtimeGraph.sharedGraph,
                    playerRuntimeServices = runtimeGraph.playerRuntimeServices,
                )
            } catch (error: Throwable) {
                runtimeGraph.disposeAfterComponentBuildFailure()
                    .exceptionOrNull()
                    ?.let(error::addSuppressed)
                throw error
            }
        }
        appComponent = appComponentResult.getOrNull()
        handleExternalAudioOpenIntent(intent)

        setContent {
            val appComponent = appComponentResult.getOrNull()
            if (appComponent != null) {
                BackHandler {
                    showAutomotiveBackActionDialog()
                }
                val appDisplayScalePreset by appComponent.appDisplayScalePreset.collectAsState()
                ProvideFixedAndroidComposeDensity(appDisplayScalePreset = appDisplayScalePreset) {
                    App(
                        component = appComponent,
                        startupAutoOpenGate = startupAutoOpenViewModel.gate,
                        artworkMemoryCacheMaxSizeBytes = AUTOMOTIVE_ARTWORK_MEMORY_CACHE_MAX_SIZE_BYTES,
                    )
                }
            } else {
                StartupDatabaseErrorScreen(
                    error = appComponentResult.exceptionOrNull(),
                    showDetails = false,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleExternalAudioOpenIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        appComponent?.settingsStore?.dispatch(SettingsIntent.RecheckDesktopLyricsPermission)
        resumePlaybackAfterWakeIfNeeded()
        observeWakeResumeUntilSettled()
    }

    override fun onPause() {
        rememberPlaybackForWake()
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        backActionDialog?.dismiss()
        backActionDialog = null
        wakeResumeJob?.cancel()
        externalAudioOpenScope.cancel()
    }

    private fun handleExternalAudioOpenIntent(intent: Intent?) {
        if (!AndroidExternalAudioOpenSupport.isExternalAudioOpenIntent(intent)) return
        val component = appComponent
        if (component == null) {
            pendingExternalAudioOpenIntent = intent?.let(::Intent)
            return
        }
        val intentSnapshot = intent?.let(::Intent) ?: pendingExternalAudioOpenIntent ?: return
        pendingExternalAudioOpenIntent = null
        val requestId = ++externalAudioOpenRequestId
        externalAudioOpenJob?.cancel()
        externalAudioOpenJob = externalAudioOpenScope.launch {
            val tracks = AndroidExternalAudioOpenSupport.tracksFromIntent(
                context = this@MainActivity,
                intent = intentSnapshot,
                logger = component.logger,
            )
            if (requestId != externalAudioOpenRequestId) return@launch
            if (tracks.isEmpty()) {
                Toast.makeText(this@MainActivity, "无法打开该音频文件。", Toast.LENGTH_SHORT).show()
                return@launch
            }
            component.playerStore.dispatch(PlayerIntent.PlayTransientTracks(tracks, 0))
        }
    }

    private fun resumePlaybackAfterWakeIfNeeded() {
        val component = appComponent ?: return
        val state = component.playerStore.state.value
        if (!wakeResumeViewModel.shouldResume(state)) return
        wakeResumeViewModel.markResumeDispatched()
        wakeResumeJob?.cancel()
        wakeResumeJob = null
        component.playerStore.dispatch(PlayerIntent.ResumeCurrentTrackPlayback)
    }

    private fun observeWakeResumeUntilSettled() {
        if (!wakeResumeViewModel.hasPending()) return
        val component = appComponent ?: return
        if (wakeResumeJob?.isActive == true) return
        wakeResumeJob = externalAudioOpenScope.launch {
            component.playerStore.state.collect { state ->
                when {
                    wakeResumeViewModel.shouldResume(state) -> {
                        wakeResumeViewModel.markResumeDispatched()
                        component.playerStore.dispatch(PlayerIntent.ResumeCurrentTrackPlayback)
                    }
                    !wakeResumeViewModel.shouldKeepPending(state) -> {
                        wakeResumeViewModel.consume()
                        wakeResumeJob?.cancel()
                        wakeResumeJob = null
                    }
                }
            }
        }
    }

    private fun rememberPlaybackForWake() {
        wakeResumeViewModel.rememberForWake(appComponent?.playerStore?.state?.value)
    }

    private fun pausePlaybackBeforeExit() {
        val component = appComponent ?: return
        if (component.playerStore.state.value.effectiveSnapshot.isPlaying) {
            component.playerStore.dispatch(PlayerIntent.TogglePlayPause)
        }
    }

    private fun showAutomotiveBackActionDialog() {
        if (backActionDialog?.isShowing == true) return
        backActionDialog = AlertDialog.Builder(this)
            .setTitle("离开 LeonMusic？")
            .setMessage("可以让歌曲继续在后台播放，或暂停播放并退出应用。")
            .setPositiveButton("后台播放") { dialog, _ ->
                dialog.dismiss()
                moveTaskToBack(true)
            }
            .setNegativeButton("退出") { dialog, _ ->
                dialog.dismiss()
                pausePlaybackBeforeExit()
                finishAndRemoveTask()
            }
            .setOnDismissListener {
                backActionDialog = null
            }
            .show()
    }
}

private const val AUTOMOTIVE_ARTWORK_MEMORY_CACHE_MAX_SIZE_BYTES = 16L * 1024L * 1024L

internal class StartupAutoOpenViewModel : ViewModel() {
    val gate = StartupAutoOpenGate()
}

internal class AutomotiveWakeResumeViewModel : ViewModel() {
    private var pendingTrackId: String? = null
    private var resumeDispatched: Boolean = false

    fun rememberForWake(state: PlayerState?) {
        val nextTrackId = wakeResumeTrackIdOrNull(state)
        if (nextTrackId != null) {
            pendingTrackId = nextTrackId
            resumeDispatched = false
        }
    }

    fun shouldResume(state: PlayerState): Boolean {
        return shouldResumePlaybackAfterWake(
            pendingTrackId = pendingTrackId,
            resumeDispatched = resumeDispatched,
            state = state,
        )
    }

    fun shouldKeepPending(state: PlayerState): Boolean {
        return shouldKeepWakeResumePending(
            pendingTrackId = pendingTrackId,
            resumeDispatched = resumeDispatched,
            state = state,
        )
    }

    fun hasPending(): Boolean {
        return pendingTrackId != null
    }

    fun markResumeDispatched() {
        resumeDispatched = true
    }

    fun consume() {
        pendingTrackId = null
        resumeDispatched = false
    }
}

private class AutomotiveWakeScreenStateReceiver(
    private val onScreenTurnedOff: () -> Unit,
) : android.content.BroadcastReceiver() {
    override fun onReceive(context: android.content.Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_SCREEN_OFF) {
            onScreenTurnedOff()
        }
    }
}

internal fun wakeResumeTrackIdOrNull(state: PlayerState?): String? {
    val snapshot = state?.effectiveSnapshot ?: return null
    return snapshot.currentTrack
        ?.takeIf { snapshot.isPlaying }
        ?.id
}

internal fun shouldResumePlaybackAfterWake(
    pendingTrackId: String?,
    resumeDispatched: Boolean = false,
    state: PlayerState,
): Boolean {
    val snapshot = state.effectiveSnapshot
    val currentTrackId = snapshot.currentTrack?.id ?: return false
    if (
        pendingTrackId == null ||
        pendingTrackId != currentTrackId ||
        snapshot.isPlaying ||
        snapshot.isHydratingPlayback
    ) {
        return false
    }
    return !isWakeResumeDispatchInFlight(resumeDispatched, state) ||
        isWakeResumeWaitingForNetwork(state)
}

internal fun shouldKeepWakeResumePending(
    pendingTrackId: String?,
    resumeDispatched: Boolean = false,
    state: PlayerState,
): Boolean {
    val snapshot = state.effectiveSnapshot
    val currentTrackId = snapshot.currentTrack?.id ?: return false
    return pendingTrackId != null &&
        pendingTrackId == currentTrackId &&
        !snapshot.isPlaying &&
        (
            snapshot.isHydratingPlayback ||
                isWakeResumeDispatchInFlight(resumeDispatched, state) ||
                isWakeResumeWaitingForNetwork(state)
            )
}

private fun isWakeResumeDispatchInFlight(
    resumeDispatched: Boolean,
    state: PlayerState,
): Boolean {
    return resumeDispatched && state.effectiveSnapshot.errorMessage == null
}

private fun isWakeResumeWaitingForNetwork(state: PlayerState): Boolean {
    return state.effectiveSnapshot.errorMessage?.contains(WAKE_RESUME_WAITING_NETWORK_KEYWORD) == true
}

private const val WAKE_RESUME_WAITING_NETWORK_KEYWORD = "等待网络连接"

@Composable
private fun ProvideFixedAndroidComposeDensity(
    appDisplayScalePreset: AppDisplayScalePreset,
    content: @Composable () -> Unit,
) {
    val currentDensity = LocalDensity.current
    val fixedDensity = remember(currentDensity.density, currentDensity.fontScale, appDisplayScalePreset) {
        Density(
            density = effectiveAppDisplayDensity(androidStableDensityScale(currentDensity.density), appDisplayScalePreset),
            fontScale = currentDensity.fontScale,
        )
    }
    CompositionLocalProvider(LocalDensity provides fixedDensity) {
        content()
    }
}

private fun ComponentActivity.isTabletIgnoringDisplaySize(): Boolean {
    val (widthPx, heightPx) = currentDisplayPx()
    val stableDensity = androidStableDensityScale(resources.displayMetrics.density)
    if (widthPx == null || heightPx == null || stableDensity <= 0f) return false
    val smallestWidthDp = min(widthPx, heightPx) / stableDensity
    return smallestWidthDp >= 600f
}

private fun ComponentActivity.currentDisplayPx(): Pair<Int?, Int?> {
    val displayMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        display?.mode
    } else {
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.mode
    }
    val width = displayMode?.physicalWidth?.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels.takeIf { it > 0 }
    val height = displayMode?.physicalHeight?.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels.takeIf { it > 0 }
    return width to height
}

private fun androidStableDensityScale(fallbackDensity: Float): Float {
    val fallbackDpi = (fallbackDensity.takeIf { it > 0f } ?: 1f) * DisplayMetrics.DENSITY_DEFAULT
    val stableDpi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        DisplayMetrics.DENSITY_DEVICE_STABLE
    } else {
        fallbackDpi.roundToInt()
    }.takeIf { it > 0 } ?: fallbackDpi.roundToInt()
    return stableDpi / DisplayMetrics.DENSITY_DEFAULT.toFloat()
}
