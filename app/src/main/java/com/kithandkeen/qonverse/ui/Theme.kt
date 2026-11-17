package com.kithandkeen.qonverse.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Qonverse colours. "box" = cards, bubbles, buttons. "audio" = speaker / play buttons. */
data class QColors(
    val dark: Boolean,
    val bg: Color, val chatBg: Color, val surface: Color, val ink: Color, val muted: Color, val sub: Color,
    val line: Color, val track: Color,
    val box: Color, val onBox: Color, val boxText: Color, val boxDeep: Color, val boxSoft: Color, val boxFaint: Color,
    val audio: Color, val onAudio: Color, val audioText: Color,
    val danger: Color, val dangerSoft: Color, val dangerText: Color,
    val wallpaper: Color, val orange: Color = Color(0xFFDDA855),
)

val DayColors = QColors(
    dark = false,
    bg = Color(0xFFFBFBFD), chatBg = Color(0xFFF6F6F9), surface = Color.White, ink = Color(0xFF15171C),
    muted = Color(0xFF5B6070), sub = Color(0xFF3D414C), line = Color(0xFFE4E2DC), track = Color(0xFFECEAE4),
    box = Color(0xFF7464B8), onBox = Color.White, boxText = Color(0xFF5D4E9E), boxDeep = Color(0xFF4A3E80),
    boxSoft = Color(0xFFF1EEF8), boxFaint = Color(0xFFF8F7FC),
    audio = Color(0xFFB8D98A), onAudio = Color(0xFF15171C), audioText = Color(0xFF557A2E),
    danger = Color(0xFFB3261E), dangerSoft = Color(0xFFFDF3F2), dangerText = Color(0xFF7A1C14),
    wallpaper = Color(0xFF7464B8),
)

val DarkColors = QColors(
    dark = true,
    bg = Color(0xFF101315), chatBg = Color(0xFF0C0F11), surface = Color(0xFF1A1E21), ink = Color(0xFFECEDEA),
    muted = Color(0xFFA2A8AE), sub = Color(0xFFC4C8CC), line = Color(0xFF2C3236), track = Color(0xFF262B2F),
    box = Color(0xFF565C9E), onBox = Color.White, boxText = Color(0xFFA9B0D8), boxDeep = Color(0xFFC5CAE6),
    boxSoft = Color(0xFF1F2035), boxFaint = Color(0xFF18192A),
    audio = Color(0xFF6FB8AE), onAudio = Color(0xFF0B0B0C), audioText = Color(0xFF6FB8AE),
    danger = Color(0xFFE5675C), dangerSoft = Color(0xFF2A1715), dangerText = Color(0xFFF2B8B0),
    wallpaper = Color(0xFF8C93C8),
)

val AmoledColors = DarkColors.copy(
    bg = Color.Black, chatBg = Color.Black, surface = Color(0xFF0B0B0C), line = Color(0xFF222528),
    track = Color(0xFF17191B), boxSoft = Color(0xFF111222), boxFaint = Color(0xFF0D0D18), dangerSoft = Color(0xFF1A0B0A),
)

val LocalQ = staticCompositionLocalOf { DayColors }

object Q {
    val c: QColors @Composable get() = LocalQ.current
}

@Composable
fun QonverseTheme(mode: String, content: @Composable () -> Unit) {
    val sysDark = isSystemInDarkTheme()
    val colors = when (mode) {
        "light" -> DayColors
        "dark" -> DarkColors
        "amoled" -> AmoledColors
        else -> if (sysDark) DarkColors else DayColors
    }
    val scheme = if (colors.dark) darkColorScheme(
        primary = colors.box, onPrimary = colors.onBox, background = colors.bg, surface = colors.surface,
        onBackground = colors.ink, onSurface = colors.ink, outline = colors.line,
    ) else lightColorScheme(
        primary = colors.box, onPrimary = colors.onBox, background = colors.bg, surface = colors.surface,
        onBackground = colors.ink, onSurface = colors.ink, outline = colors.line,
    )
    CompositionLocalProvider(LocalQ provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
