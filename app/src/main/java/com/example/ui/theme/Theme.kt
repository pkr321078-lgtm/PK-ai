package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = ObsidianVoid,
    primaryContainer = DeepCobalt,
    onPrimaryContainer = NeonIce,
    secondary = CyberTeal,
    onSecondary = ObsidianVoid,
    secondaryContainer = DarkTealContainer,
    onSecondaryContainer = Color(0xFFB5FFF4),
    tertiary = StatusViolet,
    onTertiary = Color.White,
    tertiaryContainer = StatusVioletContainer,
    onTertiaryContainer = Color(0xFFE0E7FF),
    background = ObsidianVoid,
    onBackground = TitaniumText,
    surface = ObsidianSurface,
    onSurface = TitaniumText,
    surfaceVariant = ObsidianElevated,
    onSurfaceVariant = SlateMutedText,
    surfaceContainerLowest = ObsidianVoid,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianElevated,
    surfaceContainerHigh = ObsidianCard,
    surfaceContainerHighest = Color(0xFF1D2C44),
    outline = ObsidianBorder,
    outlineVariant = Color(0xFF1B283C),
    error = StatusCrimson,
    onError = Color.White,
    errorContainer = StatusCrimsonContainer,
    onErrorContainer = Color(0xFFFFD9DF)
)

private val LightColorScheme = lightColorScheme(
    primary = CobaltPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = CobaltContainerLight,
    onPrimaryContainer = Color(0xFF00202E),
    secondary = Color(0xFF0D7A72),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFF7F2),
    onSecondaryContainer = Color(0xFF002825),
    tertiary = Color(0xFF4F46E5),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0E7FF),
    onTertiaryContainer = Color(0xFF1E1B4B),
    background = TitaniumPaper,
    onBackground = DeepInk,
    surface = TitaniumSurface,
    onSurface = DeepInk,
    surfaceVariant = TitaniumElevated,
    onSurfaceVariant = SlateBodyLight,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = TitaniumPaper,
    surfaceContainer = TitaniumElevated,
    surfaceContainerHigh = Color(0xFFDCE7F5),
    surfaceContainerHighest = Color(0xFFD1DFEF),
    outline = TitaniumBorder,
    outlineVariant = Color(0xFFDDE6F2),
    error = Color(0xFFBE123C),
    onError = Color.White,
    errorContainer = Color(0xFFFFE4E9),
    onErrorContainer = Color(0xFF5C061B)
)

private val PkJarvisShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun PkJarvisTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = PkJarvisShapes,
        content = content
    )
}
