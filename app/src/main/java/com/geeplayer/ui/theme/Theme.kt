package com.geeplayer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// DLNA Receiver 品牌色 — 深紫色调
val DlnaPurple80 = Color(0xFFD0BCFF)
val DlnaPurpleGrey80 = Color(0xFFCCC2DC)
val DlnaPink80 = Color(0xFFEFB8C8)

val DlnaPurple40 = Color(0xFF6750A4)
val DlnaPurpleGrey40 = Color(0xFF625B71)
val DlnaPink40 = Color(0xFF7D5260)

// 播放界面专用色
val PlayingAccent = Color(0xFFBB86FC)
val ProgressTrack = Color(0xFF3D3D5C)
val ProgressFill = Color(0xFFBB86FC)
val LyricsActive = Color(0xFFFFFFFF)
val LyricsInactive = Color(0x80FFFFFF)
val BackgroundDark = Color(0xFF121224)
val SurfaceDark = Color(0xFF1E1E3A)

private val LightColorScheme = lightColorScheme(
    primary = DlnaPurple40,
    secondary = DlnaPurpleGrey40,
    tertiary = DlnaPink40,
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    surfaceVariant = Color(0xFFE7E0EC),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
)

private val DarkColorScheme = darkColorScheme(
    primary = DlnaPurple80,
    secondary = DlnaPurpleGrey80,
    tertiary = DlnaPink80,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = Color(0xFF49454F),
    onPrimary = Color(0xFF381E72),
    onSecondary = Color(0xFF332D41),
    onTertiary = Color(0xFF492532),
    onBackground = Color(0xFFE6E1E5),
    onSurface = Color(0xFFE6E1E5),
)

@Composable
fun DlnaReceiverTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor -> {
            val context = androidx.compose.ui.platform.LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(
            headlineLarge = TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                lineHeight = 36.sp
            ),
            headlineMedium = TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                lineHeight = 28.sp
            ),
            titleLarge = TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                lineHeight = 24.sp
            ),
            bodyLarge = TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp
            ),
            bodyMedium = TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp
            ),
            labelSmall = TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        ),
        shapes = Shapes(
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(20.dp),
        ),
        content = content
    )
}
