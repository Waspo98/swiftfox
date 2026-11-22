package app.overbay.swiftfox.ui.theme

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.WorkOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun SwiftfoxEmblem(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    primaryColor: Color = LocalSwiftfoxColors.current.accentSplash,
    highlightColor: Color = MaterialTheme.colorScheme.primary,
    muzzleColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest
) {
    Spacer(
        modifier = modifier
            .size(size)
            .drawWithCache {
                val w = this.size.width
                val h = this.size.height

                val baseHead = Path().apply {
                    moveTo(w * 0.14f, h * 0.16f)
                    lineTo(w * 0.38f, h * 0.34f)
                    lineTo(w * 0.62f, h * 0.34f)
                    lineTo(w * 0.86f, h * 0.16f)
                    lineTo(w * 0.80f, h * 0.60f)
                    lineTo(w * 0.50f, h * 0.88f)
                    lineTo(w * 0.20f, h * 0.60f)
                    close()
                }
                val leftFacet = Path().apply {
                    moveTo(w * 0.14f, h * 0.16f)
                    lineTo(w * 0.38f, h * 0.34f)
                    lineTo(w * 0.50f, h * 0.60f)
                    lineTo(w * 0.20f, h * 0.60f)
                    close()
                }
                val muzzle = Path().apply {
                    moveTo(w * 0.34f, h * 0.60f)
                    lineTo(w * 0.50f, h * 0.88f)
                    lineTo(w * 0.66f, h * 0.60f)
                    lineTo(w * 0.50f, h * 0.68f)
                    close()
                }
                val nose = Path().apply {
                    moveTo(w * 0.44f, h * 0.79f)
                    lineTo(w * 0.50f, h * 0.88f)
                    lineTo(w * 0.56f, h * 0.79f)
                    close()
                }

                onDrawBehind {
                    drawPath(baseHead, color = primaryColor)
                    drawPath(leftFacet, color = highlightColor.copy(alpha = 0.85f))
                    drawPath(muzzle, color = muzzleColor)
                    drawPath(nose, color = Color(0xFF18161B))
                }
            }
    )
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
