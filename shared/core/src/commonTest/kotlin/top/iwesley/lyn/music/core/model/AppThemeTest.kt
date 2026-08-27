package top.iwesley.lyn.music.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppThemeTest {
    @Test
    fun `new preset themes expose their configured colors`() {
        assertEquals(
            AppThemeTokens(
                backgroundArgb = 0xFF01847E.toInt(),
                accentArgb = 0xFFE45A40.toInt(),
                focusArgb = 0xFFE45A40.toInt(),
            ),
            presetThemeTokens(AppThemeId.TigerLily),
        )
        assertEquals(
            AppThemeTokens(
                backgroundArgb = 0xFF3C4252.toInt(),
                accentArgb = 0xFF66D3C0.toInt(),
                focusArgb = 0xFF66D3C0.toInt(),
            ),
            presetThemeTokens(AppThemeId.TiffanyBlue),
        )
        assertEquals(
            AppThemeTokens(
                backgroundArgb = 0xFF0B3868.toInt(),
                accentArgb = 0xFFB89076.toInt(),
                focusArgb = 0xFFB89076.toInt(),
            ),
            presetThemeTokens(AppThemeId.PrussianBlue),
        )
    }

    @Test
    fun `new preset themes default to white text and retain independent selections`() {
        val defaults = defaultThemeTextPalettePreferences()
        assertEquals(AppThemeTextPalette.White, defaults.paletteFor(AppThemeId.TigerLily))
        assertEquals(AppThemeTextPalette.White, defaults.paletteFor(AppThemeId.TiffanyBlue))
        assertEquals(AppThemeTextPalette.White, defaults.paletteFor(AppThemeId.PrussianBlue))
        assertEquals(
            AppThemeTextPalette.Black,
            defaults.withThemePalette(AppThemeId.TiffanyBlue, AppThemeTextPalette.Black)
                .paletteFor(AppThemeId.TiffanyBlue),
        )
    }

    @Test
    fun `theme hex color parser accepts six digit RGB and rejects invalid values`() {
        assertEquals(0xFF237F6D.toInt(), parseThemeHexColor("#237f6d"))
        assertEquals(0xFF237F6D.toInt(), parseThemeHexColor("237F6D"))
        assertNull(parseThemeHexColor("#237F6"))
        assertNull(parseThemeHexColor("#237F6G"))
    }
}
