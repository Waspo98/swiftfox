package app.overbay.swiftfox.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.WorkOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.overbay.swiftfox.BuildConfig

enum class SwiftfoxEmblemVariant {
    ORANGE,
    PURPLE
}

private data class EmblemPalette(
    val base: Color,
    val highlight: Color,
    val shadow: Color,
    val muzzle: Color,
    val nose: Color,
    val iconBackdrop: Color,
    val iconHorizon: Color
)

private val OrangeEmblemPalette = EmblemPalette(
    base = Color(0xFFE86A17),
    highlight = Color(0xFFFF964A),
    shadow = Color(0xFFC6500A),
    muzzle = Color(0xFFFFF1E6),
    nose = Color(0xFF18161B),
    iconBackdrop = Color(0xFF18161B),
    iconHorizon = Color(0xFF261A14)
)

private val PurpleEmblemPalette = EmblemPalette(
    base = Color(0xFF8B5CF6),
    highlight = Color(0xFFB794FF),
    shadow = Color(0xFF6034B8),
    muzzle = Color(0xFFF4EEFF),
    nose = Color(0xFF18161B),
    iconBackdrop = Color(0xFF18161B),
    iconHorizon = Color(0xFF231934)
)

object SwiftfoxBrand {
    val isBetaBuild: Boolean
        get() = BuildConfig.APPLICATION_ID.endsWith(".beta") ||
            BuildConfig.VERSION_NAME.contains("beta", ignoreCase = true)

    val appChannelVariant: SwiftfoxEmblemVariant
        get() = if (isBetaBuild) SwiftfoxEmblemVariant.PURPLE else SwiftfoxEmblemVariant.ORANGE
}

private fun paletteFor(variant: SwiftfoxEmblemVariant): EmblemPalette = when (variant) {
    SwiftfoxEmblemVariant.ORANGE -> OrangeEmblemPalette
    SwiftfoxEmblemVariant.PURPLE -> PurpleEmblemPalette
}

@Composable
fun SwiftfoxEmblem(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    variant: SwiftfoxEmblemVariant = SwiftfoxEmblemVariant.ORANGE
) {
    val palette = paletteFor(variant)
    Spacer(
        modifier = modifier
            .size(size)
            .drawWithCache {
                val w = this.size.width
                val h = this.size.height
                val scale = minOf(w / 46f, h / 41f) * 0.92f
                val ox = (w - 46f * scale) / 2f - 31f * scale
                val oy = (h - 41f * scale) / 2f - 34f * scale
                fun px(x: Float) = ox + x * scale
                fun py(y: Float) = oy + y * scale

                val baseHead = Path().apply {
                    moveTo(px(31f), py(34f))
                    lineTo(px(45f), py(44f))
                    lineTo(px(63f), py(44f))
                    lineTo(px(77f), py(34f))
                    lineTo(px(73f), py(58f))
                    lineTo(px(54f), py(75f))
                    lineTo(px(35f), py(58f))
                    close()
                }
                val leftFacet = Path().apply {
                    moveTo(px(31f), py(34f))
                    lineTo(px(45f), py(44f))
                    lineTo(px(54f), py(58f))
                    lineTo(px(35f), py(58f))
                    close()
                }
                val rightFacet = Path().apply {
                    moveTo(px(77f), py(34f))
                    lineTo(px(63f), py(44f))
                    lineTo(px(54f), py(58f))
                    lineTo(px(73f), py(58f))
                    close()
                }
                val muzzle = Path().apply {
                    moveTo(px(43f), py(58f))
                    lineTo(px(54f), py(75f))
                    lineTo(px(65f), py(58f))
                    lineTo(px(54f), py(63f))
                    close()
                }
                val nose = Path().apply {
                    moveTo(px(50.5f), py(69.5f))
                    lineTo(px(54f), py(75f))
                    lineTo(px(57.5f), py(69.5f))
                    close()
                }

                onDrawBehind {
                    drawPath(baseHead, color = palette.base)
                    drawPath(leftFacet, color = palette.highlight)
                    drawPath(rightFacet, color = palette.shadow)
                    drawPath(muzzle, color = palette.muzzle)
                    drawPath(nose, color = palette.nose)
                }
            }
    )
}

@Composable
fun SwiftfoxAppIcon(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    variant: SwiftfoxEmblemVariant = SwiftfoxBrand.appChannelVariant
) {
    val palette = paletteFor(variant)
    val shape = RoundedCornerShape(size * 0.24f)
    Surface(
        shape = shape,
        color = palette.iconBackdrop,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.09f)),
        modifier = modifier
            .size(size)
            .clip(shape)
    ) {
        Spacer(
            modifier = Modifier
                .size(size)
                .drawWithCache {
                    // Map the 72x72 adaptive safe zone (18..90 of 108) directly into the squircle
                    val s = minOf(this.size.width, this.size.height) / 72f
                    val ox = (this.size.width - 72f * s) / 2f - 18f * s
                    val oy = (this.size.height - 72f * s) / 2f - 18f * s
                    fun px(x: Float) = ox + x * s
                    fun py(y: Float) = oy + y * s

                    val horizon = Path().apply {
                        moveTo(px(0f), py(70f))
                        cubicTo(px(28f), py(58f), px(76f), py(56f), px(108f), py(74f))
                        lineTo(px(108f), py(108f))
                        lineTo(px(0f), py(108f))
                        close()
                    }
                    val baseHead = Path().apply {
                        moveTo(px(31f), py(34f))
                        lineTo(px(45f), py(44f))
                        lineTo(px(63f), py(44f))
                        lineTo(px(77f), py(34f))
                        lineTo(px(73f), py(58f))
                        lineTo(px(54f), py(75f))
                        lineTo(px(35f), py(58f))
                        close()
                    }
                    val leftFacet = Path().apply {
                        moveTo(px(31f), py(34f))
                        lineTo(px(45f), py(44f))
                        lineTo(px(54f), py(58f))
                        lineTo(px(35f), py(58f))
                        close()
                    }
                    val rightFacet = Path().apply {
                        moveTo(px(77f), py(34f))
                        lineTo(px(63f), py(44f))
                        lineTo(px(54f), py(58f))
                        lineTo(px(73f), py(58f))
                        close()
                    }
                    val muzzle = Path().apply {
                        moveTo(px(43f), py(58f))
                        lineTo(px(54f), py(75f))
                        lineTo(px(65f), py(58f))
                        lineTo(px(54f), py(63f))
                        close()
                    }
                    val nose = Path().apply {
                        moveTo(px(50.5f), py(69.5f))
                        lineTo(px(54f), py(75f))
                        lineTo(px(57.5f), py(69.5f))
                        close()
                    }

                    onDrawBehind {
                        clipRect {
                            drawRect(palette.iconBackdrop)
                            drawPath(horizon, color = palette.iconHorizon)
                            drawPath(baseHead, color = palette.base)
                            drawPath(leftFacet, color = palette.highlight)
                            drawPath(rightFacet, color = palette.shadow)
                            drawPath(muzzle, color = palette.muzzle)
                            drawPath(nose, color = palette.nose)
                        }
                    }
                }
        )
    }
}

@Composable
fun SwiftfoxWordmark(
    modifier: Modifier = Modifier,
    text: String = "Swiftfox",
    fontSize: TextUnit = 32.sp,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Text(
        text = text,
        style = TextStyle(
            fontFamily = SoraFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
            lineHeight = fontSize * 1.1f,
            letterSpacing = (-0.025f).em
        ),
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
fun BetaChannelPill(
    modifier: Modifier = Modifier,
    label: String = "BETA"
) {
    val foxColors = LocalSwiftfoxColors.current
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = foxColors.privateContainer,
        border = BorderStroke(1.dp, foxColors.privateAccent.copy(alpha = 0.45f)),
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = SoraFontFamily,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.08.em
            ),
            color = foxColors.privateAccent,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun SwiftfoxBrandLockup(
    modifier: Modifier = Modifier,
    emblemSize: Dp = 38.dp,
    wordmarkSize: TextUnit = 32.sp,
    variant: SwiftfoxEmblemVariant = SwiftfoxEmblemVariant.ORANGE,
    wordmarkColor: Color = MaterialTheme.colorScheme.primary,
    showBetaBadge: Boolean = SwiftfoxBrand.isBetaBuild,
    spacing: Dp = 10.dp
) {
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {
            role = Role.Image
            contentDescription = if (showBetaBadge) "Swiftfox Beta" else "Swiftfox"
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing)
    ) {
        SwiftfoxEmblem(
            size = emblemSize,
            variant = variant
        )
        SwiftfoxWordmark(
            fontSize = wordmarkSize,
            color = wordmarkColor
        )
        if (showBetaBadge) {
            BetaChannelPill()
        }
    }
}

fun spaceIconFor(key: String): ImageVector = when (key) {
    "home" -> Icons.Rounded.Home
    "work" -> Icons.Rounded.WorkOutline
    "book" -> Icons.Rounded.AutoStories
    "code" -> Icons.Rounded.Code
    "shop" -> Icons.Rounded.ShoppingBag
    "star" -> Icons.Rounded.Star
    else -> Icons.Rounded.Layers
}
