package app.overbay.swiftfox.features.foldable

import android.app.Activity
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowMetricsCalculator
import app.overbay.swiftfox.data.prefs.DevicePosture
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

data class FoldableWindowState(
    val posture: DevicePosture = DevicePosture.FOLDED,
    val windowWidthDp: Int = 412
)

object PostureObserver {

    fun observe(activity: Activity): Flow<FoldableWindowState> {
        return WindowInfoTracker.getOrCreate(activity)
            .windowLayoutInfo(activity)
            .map { layoutInfo ->
                val metrics = WindowMetricsCalculator.getOrCreate()
                    .computeCurrentWindowMetrics(activity)
                val density = activity.resources.displayMetrics.density.coerceAtLeast(0.5f)
                val widthDp = (metrics.bounds.width() / density).toInt()
                val heightDp = (metrics.bounds.height() / density).toInt()
                val smallestWidthDp = minOf(widthDp, heightDp)

                val foldingFeature = layoutInfo.displayFeatures
                    .filterIsInstance<FoldingFeature>()
                    .firstOrNull()

                val isVerticalHingeUnfolded = foldingFeature != null &&
                    foldingFeature.orientation == FoldingFeature.Orientation.VERTICAL &&
                    (foldingFeature.state == FoldingFeature.State.FLAT ||
                        foldingFeature.state == FoldingFeature.State.HALF_OPENED) &&
                    widthDp >= 600

                // Require smallestWidthDp >= 600 or an active wide vertical-hinge book/flat layout so
                // clamshell flip foldables and rotated cover screens stay classified as FOLDED.
                val isUnfolded = smallestWidthDp >= 600 || isVerticalHingeUnfolded

                FoldableWindowState(
                    posture = if (isUnfolded) DevicePosture.UNFOLDED else DevicePosture.FOLDED,
                    windowWidthDp = widthDp
                )
            }
            .distinctUntilChanged()
    }
}
