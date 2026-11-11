package uz.himoya.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object AppColors {
    val Bg = Color(0xFFFAF9F6)
    val Surface = Color(0xFFFFFFFF)
    val Text = Color(0xFF18181B)
    val TextMuted = Color(0xFF71717A)
    val Border = Color(0xFFE4E4E7)
    val Safe = Color(0xFF059669)
    val Warn = Color(0xFFD97706)
    val Danger = Color(0xFFDC2626)
    val Accent = Color(0xFF0F766E)
}

private val Scheme = lightColorScheme(
    primary = AppColors.Accent,
    onPrimary = Color.White,
    background = AppColors.Bg,
    onBackground = AppColors.Text,
    surface = AppColors.Surface,
    onSurface = AppColors.Text,
    outline = AppColors.Border,
    error = AppColors.Danger,
)

// Matnlar ataylab yirik: ilova bir qarashda o'qiladigan bo'lishi kerak.
private val Base = Typography()
private val AppTypography = Base.copy(
    headlineLarge = Base.headlineLarge.copy(fontSize = 38.sp, lineHeight = 46.sp, fontWeight = FontWeight.Bold),
    titleLarge = Base.titleLarge.copy(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = Base.bodyLarge.copy(fontSize = 20.sp, lineHeight = 28.sp),
    bodyMedium = Base.bodyMedium.copy(fontSize = 17.sp, lineHeight = 24.sp),
    labelLarge = Base.labelLarge.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = AppTypography, content = content)
}
