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
    val isTabletop: Boolean = false,
    val isBookPosture: Boolean = false,
    val windowWidthDp: Int = 412
)

object PostureObserver {

    fun observe(activity: Activity): Flow<FoldableWindowState> {
        return WindowInfoTracker.getOrCreate(activity)
            .windowLayoutInfo(activity)
            .map { layoutInfo ->
                val metrics = WindowMetricsCalculator.getOrCreate()
                    .computeCurrentWindowMetrics(activity)
                val density = activity.resources.displayMetrics.density.coerceAtLeast(1f)
                val widthDp = (metrics.bounds.width() / density).toInt()

                val foldingFeature = layoutInfo.displayFeatures
                    .filterIsInstance<FoldingFeature>()
                    .firstOrNull()

                val isTabletop = foldingFeature != null &&
                    foldingFeature.state == FoldingFeature.State.HALF_OPENED &&
                    foldingFeature.orientation == FoldingFeature.Orientation.HORIZONTAL

                val isBook = foldingFeature != null &&
                    foldingFeature.state == FoldingFeature.State.HALF_OPENED &&
                    foldingFeature.orientation == FoldingFeature.Orientation.VERTICAL

                val isUnfolded = widthDp >= 600 ||
                    (foldingFeature != null &&
                        (foldingFeature.state == FoldingFeature.State.FLAT ||
                            foldingFeature.state == FoldingFeature.State.HALF_OPENED))

                FoldableWindowState(
                    posture = if (isUnfolded) DevicePosture.UNFOLDED else DevicePosture.FOLDED,
                    isTabletop = isTabletop,
                    isBookPosture = isBook,
                    windowWidthDp = widthDp
                )
            }
            .distinctUntilChanged()
    }
}
