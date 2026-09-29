// SPDX-License-Identifier: GPL-3.0-only
// © 2026 cognitiveshadows03 — Lunar Labs
package dev.citali.repquest.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.citali.gami.Rarity

// Lunar-dark surfaces + energetic lime. Dark-first brand; light is supported.
private val Lime = Color(0xFFBEF264)
private val LimeDeep = Color(0xFF4D7C0F)
private val TealPop = Color(0xFF5EEAD4)
private val NightBg = Color(0xFF0C0E0C)
private val NightSurface = Color(0xFF141714)
private val NightContainer = Color(0xFF1D211D)
private val DayBg = Color(0xFFF6F8F1)

private val DarkScheme =
    darkColorScheme(
        primary = Lime,
        onPrimary = Color(0xFF1A2E05),
        primaryContainer = Color(0xFF365314),
        onPrimaryContainer = Color(0xFFECFCCB),
        secondary = TealPop,
        onSecondary = Color(0xFF042F2E),
        secondaryContainer = Color(0xFF134E4A),
        onSecondaryContainer = Color(0xFFCCFBF1),
        tertiary = Color(0xFFF0ABFC),
        background = NightBg,
        onBackground = Color(0xFFE7EDE3),
        surface = NightSurface,
        onSurface = Color(0xFFE7EDE3),
        surfaceContainerHigh = NightContainer,
        surfaceContainerHighest = Color(0xFF262B26),
    )

private val LightScheme =
    lightColorScheme(
        primary = LimeDeep,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD9F99D),
        onPrimaryContainer = Color(0xFF1A2E05),
        secondary = Color(0xFF0F766E),
        tertiary = Color(0xFFA21CAF),
        background = DayBg,
        surface = Color.White,
        surfaceContainerHigh = Color(0xFFEFF2E8),
        surfaceContainerHighest = Color(0xFFE6EADF),
    )

@Composable
fun RepQuestTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        content = content,
    )
}

/** Rarity gradient pairs (top → bottom) + chip tint. Shared by every screen. */
object RarityTheme {
    fun gradient(rarity: Rarity): Pair<Color, Color> =
        when (rarity) {
            Rarity.COMMON -> Color(0xFF9AA0A6) to Color(0xFF5F6368)
            Rarity.RARE -> Color(0xFF7DD3FC) to Color(0xFF2563EB)
            Rarity.EPIC -> Color(0xFFD8B4FE) to Color(0xFF9333EA)
            Rarity.LEGENDARY -> Color(0xFFFDE68A) to Color(0xFFF59E0B)
        }

    fun chip(rarity: Rarity): Color =
        when (rarity) {
            Rarity.COMMON -> Color(0xFF9AA0A6)
            Rarity.RARE -> Color(0xFF38BDF8)
            Rarity.EPIC -> Color(0xFFC084FC)
            Rarity.LEGENDARY -> Color(0xFFFBBF24)
        }
}
