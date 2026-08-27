package top.iwesley.lyn.music.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import top.iwesley.lyn.music.core.model.AppThemeId
import top.iwesley.lyn.music.core.model.AppThemeTextPalettePreferences
import top.iwesley.lyn.music.core.model.AppThemePalette
import top.iwesley.lyn.music.core.model.AppThemeTokens
import top.iwesley.lyn.music.core.model.defaultThemeTextPalettePreferences
import top.iwesley.lyn.music.core.model.deriveAppThemePalette
import top.iwesley.lyn.music.core.model.resolveAppThemeTextPalette
import top.iwesley.lyn.music.core.model.resolveAppThemeTokens
import top.iwesley.lyn.music.ui.LeonMusicTheme

private val TvMainThemeTokens = AppThemeTokens(
    backgroundArgb = 0xFF0B0D10.toInt(),
    accentArgb = 0xFFE03131.toInt(),
    focusArgb = 0xFFE03131.toInt(),
)

@Composable
internal fun TvMainTheme(
    selectedTheme: AppThemeId = AppThemeId.Classic,
    customThemeTokens: AppThemeTokens = TvMainThemeTokens,
    textPalettePreferences: AppThemeTextPalettePreferences = defaultThemeTextPalettePreferences(),
    content: @Composable () -> Unit,
) {
    val themeTokens = remember(selectedTheme, customThemeTokens) {
        resolveAppThemeTokens(selectedTheme, customThemeTokens)
    }
    val textPalette = remember(selectedTheme, textPalettePreferences) {
        resolveAppThemeTextPalette(selectedTheme, textPalettePreferences)
    }
    val palette = remember(themeTokens, textPalette) {
        deriveAppThemePalette(themeTokens, textPalette)
    }
    LeonMusicTheme(
        themeTokens = themeTokens,
        textPalette = textPalette,
    ) {
        androidx.tv.material3.MaterialTheme(
            colorScheme = palette.toTvColorScheme(),
            content = content,
        )
    }
}

private fun AppThemePalette.toTvColorScheme(): androidx.tv.material3.ColorScheme {
    return androidx.tv.material3.darkColorScheme(
        primary = Color(primaryArgb),
        onPrimary = Color(onPrimaryArgb),
        primaryContainer = Color(selectedContainerArgb),
        onPrimaryContainer = Color(onBackgroundArgb),
        secondary = Color(secondaryArgb),
        onSecondary = Color(onSecondaryArgb),
        secondaryContainer = Color(selectedContainerArgb),
        onSecondaryContainer = Color(onBackgroundArgb),
        tertiary = Color(tertiaryArgb),
        onTertiary = Color(onTertiaryArgb),
        tertiaryContainer = Color(cardContainerArgb),
        onTertiaryContainer = Color(onSurfaceArgb),
        background = Color(backgroundArgb),
        onBackground = Color(onBackgroundArgb),
        surface = Color(surfaceArgb),
        onSurface = Color(onSurfaceArgb),
        surfaceVariant = Color(surfaceVariantArgb),
        onSurfaceVariant = Color(onSurfaceVariantArgb),
        surfaceTint = Color(primaryArgb),
        inverseSurface = Color(onSurfaceArgb),
        inverseOnSurface = Color(surfaceArgb),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        border = Color(outlineArgb),
        borderVariant = Color(cardBorderArgb),
        scrim = Color(0x99000000),
    )
}
