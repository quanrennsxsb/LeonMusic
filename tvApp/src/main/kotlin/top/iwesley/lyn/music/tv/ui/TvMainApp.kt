package top.iwesley.lyn.music.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import top.iwesley.lyn.music.LeonMusicAppComponent
import top.iwesley.lyn.music.feature.favorites.FavoritesIntent
import top.iwesley.lyn.music.feature.importing.ImportIntent
import top.iwesley.lyn.music.feature.library.LibraryIntent
import top.iwesley.lyn.music.feature.player.PlayerIntent
import top.iwesley.lyn.music.feature.playlists.PlaylistsIntent
import top.iwesley.lyn.music.feature.settings.SettingsIntent
import top.iwesley.lyn.music.tv.ConfigureTvImageLoader
import top.iwesley.lyn.music.tv.TvPlayerActivity

@Composable
internal fun TvMainApp(
    component: LeonMusicAppComponent,
) {
    ConfigureTvImageLoader()

    val scope = rememberCoroutineScope()
    val tvStore = remember(component) { TvMainStore(scope) }
    val tvState by tvStore.state.collectAsState()
    val myState by component.myStore.state.collectAsState()
    val libraryState by component.libraryStore.state.collectAsState()
    val favoritesState by component.favoritesStore.state.collectAsState()
    val playlistsState by component.playlistsStore.state.collectAsState()
    val importState by component.importStore.state.collectAsState()
    val playerState by component.playerStore.state.collectAsState()
    val settingsState by component.settingsStore.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(component, tvStore) {
        component.playerStore.startHydration()
        component.settingsStore.dispatch(SettingsIntent.CheckAppUpdateSilently)
        tvStore.dispatch(TvMainIntent.ActivateDestination(TvMainDestination.Library))
    }

    LaunchedEffect(component, tvStore) {
        tvStore.effects.collect { effect ->
            when (effect) {
                TvMainEffect.StartMy -> component.myStore.ensureStarted()
                TvMainEffect.StartLibrary -> component.libraryStore.ensureStarted()
                TvMainEffect.StartFavorites -> component.favoritesStore.ensureContentStarted()
                TvMainEffect.StartPlaylists -> component.playlistsStore.ensureContentStarted()
                is TvMainEffect.SelectPlaylist -> component.playlistsStore.dispatch(
                    PlaylistsIntent.SelectPlaylist(effect.playlistId),
                )
                is TvMainEffect.SearchLibrary ->
                    component.libraryStore.dispatch(LibraryIntent.SearchChanged(effect.query))
                is TvMainEffect.SearchFavorites ->
                    component.favoritesStore.dispatch(FavoritesIntent.SearchChanged(effect.query))
                is TvMainEffect.PlayTracks -> {
                    component.playerStore.dispatch(
                        PlayerIntent.PlayTracks(effect.tracks, effect.startIndex, effect.requestedMode),
                    )
                    context.startActivity(TvPlayerActivity.createIntent(context))
                }
                is TvMainEffect.ToggleFavorite ->
                    component.favoritesStore.dispatch(FavoritesIntent.ToggleFavorite(effect.track))
            }
        }
    }

    LaunchedEffect(
        tvState.selectedDestination,
        playlistsState.isLoadingContent,
        playlistsState.playlists,
        playlistsState.selectedPlaylistId,
    ) {
        if (
            tvState.selectedDestination == TvMainDestination.Playlists &&
            !playlistsState.isLoadingContent &&
            playlistsState.playlists.isNotEmpty() &&
            playlistsState.selectedPlaylistId == null
        ) {
            tvStore.dispatch(TvMainIntent.OpenPlaylist(playlistsState.playlists.first().id))
        }
    }

    TvMainTheme(
        selectedTheme = settingsState.selectedTheme,
        customThemeTokens = settingsState.customThemeTokens,
        textPalettePreferences = settingsState.textPalettePreferences,
    ) {
        TvMainScreen(
            state = tvState,
            myState = myState,
            libraryState = libraryState,
            favoritesState = favoritesState,
            playlistsState = playlistsState,
            playerState = playerState,
            showSettingsUpdateBadge = settingsState.appUpdateHasNewVersion == true,
            isRefreshingLibraries = importState.isWorking,
            artworkCacheStore = component.artworkCacheStore,
            onIntent = tvStore::dispatch,
            onPlayerIntent = component.playerStore::dispatch,
            onRefreshLibraries = { component.importStore.dispatch(ImportIntent.SyncAllSources) },
        )
    }
}
