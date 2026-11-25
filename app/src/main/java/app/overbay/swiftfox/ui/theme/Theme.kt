package app.overbay.swiftfox.ui.theme

import android.app.Activity
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.overbay.swiftfox.R
import app.overbay.swiftfox.data.prefs.AccentPreset
import app.overbay.swiftfox.data.prefs.NightSurfaceStyle
import app.overbay.swiftfox.data.prefs.ThemeMode

internal val LocalReduceMotion = staticCompositionLocalOf { false }

private fun querySystemReduceMotion(context: Context): Boolean =
    runCatching {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }.getOrDefault(false)
val DaySurface = Color(0xFFFAF8F6)
val DaySurfaceLow = Color(0xFFF5F1ED)
val DaySurfaceContainer = Color(0xFFEFEAE4)
val DaySurfaceHigh = Color(0xFFE6DFD8)
val DaySurfaceHighest = Color(0xFFDCD3CA)
val DayOnSurface = Color(0xFF1C1917)
val DayOnSurfaceVariant = Color(0xFF57504B)
val DayOutline = Color(0xFF837870)
val DayOutlineVariant = Color(0xFFD5CCC4)

val DarkSurface = Color(0xFF18161B)
val DarkSurfaceLow = Color(0xFF1E1C22)
val DarkSurfaceContainer = Color(0xFF232028)
val DarkSurfaceHigh = Color(0xFF2D2933)
val DarkSurfaceHighest = Color(0xFF383340)
val DarkOnSurface = Color(0xFFECE7F0)
val DarkOnSurfaceVariant = Color(0xFFC2BAC9)
val DarkOutline = Color(0xFF8C8494)
val DarkOutlineVariant = Color(0xFF3E3947)

val AmoledSurface = Color(0xFF000000)
val AmoledSurfaceLow = Color(0xFF0A090D)
val AmoledSurfaceContainer = Color(0xFF121016)
val AmoledSurfaceHigh = Color(0xFF1C1922)
val AmoledSurfaceHighest = Color(0xFF272330)
val AmoledOnSurface = Color(0xFFF3EEF7)
val AmoledOnSurfaceVariant = Color(0xFFC4BCD0)
val AmoledOutline = Color(0xFF8A8294)
val AmoledOutlineVariant = Color(0xFF2E2A36)

// Signature Fox Amber tokens
val FoxAmberDayPrimary = Color(0xFFB04200)
val FoxAmberDayContainer = Color(0xFFFFE2CC)
val FoxAmberDayOnContainer = Color(0xFF4A1B00)
val FoxAmberDarkPrimary = Color(0xFFFF9147)
val FoxAmberDarkOnPrimary = Color(0xFF2B0F00)
val FoxAmberDarkContainer = Color(0xFF3A1F0D)
val FoxAmberDarkOnContainer = Color(0xFFFFDEC7)

// Midnight Indigo tokens
val IndigoDayPrimary = Color(0xFF3D4ED8)
val IndigoDayContainer = Color(0xFFDFE3FF)
val IndigoDayOnContainer = Color(0xFF0C1566)
val IndigoDarkPrimary = Color(0xFF93A0FF)
val IndigoDarkOnPrimary = Color(0xFF0D1763)
val IndigoDarkContainer = Color(0xFF202652)
val IndigoDarkOnContainer = Color(0xFFE1E5FF)

// Forest tokens
val ForestDayPrimary = Color(0xFF1D6E42)
val ForestDayContainer = Color(0xFFCFF3DC)
val ForestDayOnContainer = Color(0xFF043018)
val ForestDarkPrimary = Color(0xFF6CD998)
val ForestDarkOnPrimary = Color(0xFF002B14)
val ForestDarkContainer = Color(0xFF123622)
val ForestDarkOnContainer = Color(0xFFCBF5DA)

// Monochrome tokens
val MonoDayPrimary = Color(0xFF2C2A2F)
val MonoDayContainer = Color(0xFFE3E0E6)
val MonoDayOnContainer = Color(0xFF19171C)
val MonoDarkPrimary = Color(0xFFDCD8E0)
val MonoDarkOnPrimary = Color(0xFF1B191E)
val MonoDarkContainer = Color(0xFF312E36)
val MonoDarkOnContainer = Color(0xFFEFECF2)

@Immutable
data class SwiftfoxExtendedColors(
    val privateContainer: Color,
    val onPrivateContainer: Color,
    val privateAccent: Color,
    val onPrivateAccent: Color
)

val LocalSwiftfoxColors = staticCompositionLocalOf {
    SwiftfoxExtendedColors(
        privateContainer = Color(0xFF2C1E4A),
        onPrivateContainer = Color(0xFFE9DDFF),
        privateAccent = Color(0xFFB594FF),
        onPrivateAccent = Color(0xFF1F0947)
    )
}

val SwiftfoxShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

val SoraFontFamily = FontFamily(
    Font(R.font.sora_semibold, FontWeight.SemiBold),
    Font(R.font.sora_bold, FontWeight.Bold)
)

val SwiftfoxTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = SoraFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.6).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = SoraFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = SoraFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.25).sp
    ),
    titleLarge = TextStyle(
        fontFamily = SoraFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.15).sp
    ),
    titleMedium = TextStyle(
        fontFamily = SoraFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.25.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.3.sp
    )
)

private fun buildDayScheme(preset: AccentPreset): ColorScheme {
    val (primary, onPrimary, primaryContainer, onPrimaryContainer) = when (preset) {
        AccentPreset.FOX_AMBER -> Quad(FoxAmberDayPrimary, Color.White, FoxAmberDayContainer, FoxAmberDayOnContainer)
        AccentPreset.MIDNIGHT_INDIGO -> Quad(IndigoDayPrimary, Color.White, IndigoDayContainer, IndigoDayOnContainer)
        AccentPreset.FOREST -> Quad(ForestDayPrimary, Color.White, ForestDayContainer, ForestDayOnContainer)
        AccentPreset.MONOCHROME -> Quad(MonoDayPrimary, Color.White, MonoDayContainer, MonoDayOnContainer)
    }
    return lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = primary,
        onSecondary = onPrimary,
        secondaryContainer = primaryContainer,
        onSecondaryContainer = onPrimaryContainer,
        tertiary = Color(0xFF6B4FA8),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFECE2FF),
        onTertiaryContainer = Color(0xFF240F52),
        background = DaySurface,
        onBackground = DayOnSurface,
        surface = DaySurface,
        onSurface = DayOnSurface,
        surfaceVariant = DaySurfaceHigh,
        onSurfaceVariant = DayOnSurfaceVariant,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = DaySurfaceLow,
        surfaceContainer = DaySurfaceContainer,
        surfaceContainerHigh = DaySurfaceHigh,
        surfaceContainerHighest = DaySurfaceHighest,
        outline = DayOutline,
        outlineVariant = DayOutlineVariant,
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002)
    )
}

private fun buildNightScheme(preset: AccentPreset, isAmoled: Boolean): ColorScheme {
    val (primary, onPrimary, primaryContainer, onPrimaryContainer) = when (preset) {
        AccentPreset.FOX_AMBER -> Quad(FoxAmberDarkPrimary, FoxAmberDarkOnPrimary, FoxAmberDarkContainer, FoxAmberDarkOnContainer)
        AccentPreset.MIDNIGHT_INDIGO -> Quad(IndigoDarkPrimary, IndigoDarkOnPrimary, IndigoDarkContainer, IndigoDarkOnContainer)
        AccentPreset.FOREST -> Quad(ForestDarkPrimary, ForestDarkOnPrimary, ForestDarkContainer, ForestDarkOnContainer)
        AccentPreset.MONOCHROME -> Quad(MonoDarkPrimary, MonoDarkOnPrimary, MonoDarkContainer, MonoDarkOnContainer)
    }
    return darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = primary,
        onSecondary = onPrimary,
        secondaryContainer = primaryContainer,
        onSecondaryContainer = onPrimaryContainer,
        tertiary = Color(0xFFC8B3FF),
        onTertiary = Color(0xFF281259),
        tertiaryContainer = Color(0xFF2E1E52),
        onTertiaryContainer = Color(0xFFECE2FF),
        background = if (isAmoled) AmoledSurface else DarkSurface,
        onBackground = if (isAmoled) AmoledOnSurface else DarkOnSurface,
        surface = if (isAmoled) AmoledSurface else DarkSurface,
        onSurface = if (isAmoled) AmoledOnSurface else DarkOnSurface,
        surfaceVariant = if (isAmoled) AmoledSurfaceHigh else DarkSurfaceHigh,
        onSurfaceVariant = if (isAmoled) AmoledOnSurfaceVariant else DarkOnSurfaceVariant,
        surfaceContainerLowest = if (isAmoled) AmoledSurface else Color(0xFF121015),
        surfaceContainerLow = if (isAmoled) AmoledSurfaceLow else DarkSurfaceLow,
        surfaceContainer = if (isAmoled) AmoledSurfaceContainer else DarkSurfaceContainer,
        surfaceContainerHigh = if (isAmoled) AmoledSurfaceHigh else DarkSurfaceHigh,
        surfaceContainerHighest = if (isAmoled) AmoledSurfaceHighest else DarkSurfaceHighest,
        outline = if (isAmoled) AmoledOutline else DarkOutline,
        outlineVariant = if (isAmoled) AmoledOutlineVariant else DarkOutlineVariant,
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6)
    )
}

private data class Quad<T>(val first: T, val second: T, val third: T, val fourth: T)

@Composable
fun SwiftfoxTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    nightSurfaceStyle: NightSurfaceStyle = NightSurfaceStyle.DARK,
    accentPreset: AccentPreset = AccentPreset.FOX_AMBER,
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.DAY -> false
        ThemeMode.NIGHT -> true
        ThemeMode.SYSTEM -> systemDark
    }
    val isAmoled = isDark && nightSurfaceStyle == NightSurfaceStyle.AMOLED
    val context = LocalContext.current

    val colorScheme = remember(isDark, isAmoled, accentPreset, useDynamicColor, context) {
        when {
            useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val base = if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                if (isAmoled) {
                    base.copy(
                        background = AmoledSurface,
                        surface = AmoledSurface,
                        surfaceContainerLowest = AmoledSurface,
                        surfaceContainerLow = AmoledSurfaceLow,
                        surfaceContainer = AmoledSurfaceContainer,
                        surfaceContainerHigh = AmoledSurfaceHigh,
                        surfaceContainerHighest = AmoledSurfaceHighest
                    )
                } else {
                    base
                }
            }
            isDark -> buildNightScheme(accentPreset, isAmoled)
            else -> buildDayScheme(accentPreset)
        }
    }

    val extendedColors = remember(isDark) {
        SwiftfoxExtendedColors(
            privateContainer = if (isDark) Color(0xFF281B45) else Color(0xFFECE2FF),
            onPrivateContainer = if (isDark) Color(0xFFEBDFFF) else Color(0xFF261057),
            privateAccent = if (isDark) Color(0xFFC1A6FF) else Color(0xFF6342B5),
            onPrivateAccent = if (isDark) Color(0xFF1F0947) else Color.White
        )
    }

    val view = LocalView.current
    var reduceMotion by remember(context) {
        mutableStateOf(if (view.isInEditMode) false else querySystemReduceMotion(context))
    }
    if (!view.isInEditMode) {
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner, context) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    reduceMotion = querySystemReduceMotion(context)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
        val window = (view.context as? Activity)?.window
        if (window != null) {
            val controller = remember(window, view) {
                WindowCompat.getInsetsController(window, view)
            }
            SideEffect {
                controller.isAppearanceLightStatusBars = !isDark
                controller.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    CompositionLocalProvider(
        LocalSwiftfoxColors provides extendedColors,
        LocalReduceMotion provides reduceMotion
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SwiftfoxTypography,
            shapes = SwiftfoxShapes,
            content = content
        )
    }
}

@Composable
internal fun rememberReduceMotion(): Boolean = LocalReduceMotion.current

/**
 * Extracts the first Unicode code point (avoiding broken UTF-16 surrogate halves on emoji titles)
 * and uppercases ASCII/letter monograms safely.
 */
internal fun firstDisplaySymbol(text: String, fallback: String = ""): String {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return fallback
    val cp = trimmed.codePointAt(0)
    val charCount = Character.charCount(cp)
    val symbol = trimmed.substring(0, charCount)
    return if (charCount == 1) symbol.uppercase(java.util.Locale.ROOT) else symbol
}
