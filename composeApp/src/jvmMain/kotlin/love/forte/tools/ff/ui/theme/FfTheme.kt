package love.forte.tools.ff.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import love.forte.tools.ff.storage.FfAppTheme
import org.jetbrains.compose.resources.Font

import love.forte.tools.file_flattener.composeapp.generated.resources.Res
import love.forte.tools.file_flattener.composeapp.generated.resources.LXGWNeoXiHeiScreen

private val CherryRedColors = lightColorScheme(
    primary = Color(0xFFE11D48),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9E2),
    onPrimaryContainer = Color(0xFF3B0013),
    secondary = Color(0xFFFF4F79),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD9E2),
    onSecondaryContainer = Color(0xFF2A0A14),
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFF3DCE1),
    onSurfaceVariant = Color(0xFF524347),
    outline = Color(0xFF857377),
)

private val ClassicBlackColors = darkColorScheme(
    primary = Color(0xFFE11D48),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF3A0A17),
    onPrimaryContainer = Color(0xFFFFD9E2),
    secondary = Color(0xFFB8B8C2),
    onSecondary = Color(0xFF111113),
    secondaryContainer = Color(0xFF1C1C22),
    onSecondaryContainer = Color(0xFFE6E6EA),
    background = Color(0xFF0B0B0F),
    onBackground = Color(0xFFE6E6EA),
    surface = Color(0xFF111113),
    onSurface = Color(0xFFE6E6EA),
    surfaceVariant = Color(0xFF1C1C22),
    onSurfaceVariant = Color(0xFFB8B8C2),
    outline = Color(0xFF3A3A44),
)

private val FfShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
)

@Composable
fun FfTheme(
    theme: FfAppTheme,
    content: @Composable () -> Unit,
) {
    val fontFamily = rememberFfFontFamily()
    val typography = remember(fontFamily) { ffTypography(fontFamily) }

    MaterialTheme(
        colorScheme = when (theme) {
            FfAppTheme.CherryRed -> CherryRedColors
            FfAppTheme.ClassicBlack -> ClassicBlackColors
        },
        typography = typography,
        shapes = FfShapes,
        content = content,
    )
}

@Composable
private fun rememberFfFontFamily(): FontFamily {
    // 字体通过 compose.resources 访问（有预编译）。
    val regular = Font(Res.font.LXGWNeoXiHeiScreen, weight = FontWeight.Normal)
    return remember(regular) { FontFamily(regular) }
}

private fun ffTypography(fontFamily: FontFamily): Typography {
    // 说明：尽量复用默认排版 token，只替换 FontFamily，避免大量手写数值。
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = fontFamily),
        displayMedium = base.displayMedium.copy(fontFamily = fontFamily),
        displaySmall = base.displaySmall.copy(fontFamily = fontFamily),
        headlineLarge = base.headlineLarge.copy(fontFamily = fontFamily),
        headlineMedium = base.headlineMedium.copy(fontFamily = fontFamily),
        headlineSmall = base.headlineSmall.copy(fontFamily = fontFamily),
        titleLarge = base.titleLarge.copy(fontFamily = fontFamily),
        titleMedium = base.titleMedium.copy(fontFamily = fontFamily),
        titleSmall = base.titleSmall.copy(fontFamily = fontFamily),
        bodyLarge = base.bodyLarge.copy(fontFamily = fontFamily),
        bodyMedium = base.bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = base.bodySmall.copy(fontFamily = fontFamily),
        labelLarge = base.labelLarge.copy(fontFamily = fontFamily),
        labelMedium = base.labelMedium.copy(fontFamily = fontFamily),
        labelSmall = base.labelSmall.copy(fontFamily = fontFamily),
    )
}
