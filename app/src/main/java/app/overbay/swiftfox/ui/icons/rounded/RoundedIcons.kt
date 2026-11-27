package app.overbay.swiftfox.ui.icons.rounded

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector

private var _accountBalance: ImageVector? = null
val Icons.Rounded.AccountBalance: ImageVector
    get() {
        _accountBalance?.let { return it }
        return materialIcon(name = "Rounded.AccountBalance") {
            materialPath {
                moveTo(4.0f, 11.5f)
                verticalLineToRelative(4.0f)
                curveToRelative(0.0f, 0.83f, 0.67f, 1.5f, 1.5f, 1.5f)
                reflectiveCurveTo(7.0f, 16.33f, 7.0f, 15.5f)
                verticalLineToRelative(-4.0f)
                curveToRelative(0.0f, -0.83f, -0.67f, -1.5f, -1.5f, -1.5f)
                reflectiveCurveTo(4.0f, 10.67f, 4.0f, 11.5f)
                close()
                moveTo(10.0f, 11.5f)
                verticalLineToRelative(4.0f)
                curveToRelative(0.0f, 0.83f, 0.67f, 1.5f, 1.5f, 1.5f)
                reflectiveCurveToRelative(1.5f, -0.67f, 1.5f, -1.5f)
                verticalLineToRelative(-4.0f)
                curveToRelative(0.0f, -0.83f, -0.67f, -1.5f, -1.5f, -1.5f)
                reflectiveCurveToRelative(-1.5f, 0.67f, -1.5f, 1.5f)
                close()
                moveTo(3.5f, 22.0f)
                horizontalLineToRelative(16.0f)
                curveToRelative(0.83f, 0.0f, 1.5f, -0.67f, 1.5f, -1.5f)
                reflectiveCurveToRelative(-0.67f, -1.5f, -1.5f, -1.5f)
                horizontalLineToRelative(-16.0f)
                curveToRelative(-0.83f, 0.0f, -1.5f, 0.67f, -1.5f, 1.5f)
                reflectiveCurveTo(2.67f, 22.0f, 3.5f, 22.0f)
                close()
                moveTo(16.0f, 11.5f)
                verticalLineToRelative(4.0f)
                curveToRelative(0.0f, 0.83f, 0.67f, 1.5f, 1.5f, 1.5f)
                reflectiveCurveToRelative(1.5f, -0.67f, 1.5f, -1.5f)
                verticalLineToRelative(-4.0f)
                curveToRelative(0.0f, -0.83f, -0.67f, -1.5f, -1.5f, -1.5f)
                reflectiveCurveToRelative(-1.5f, 0.67f, -1.5f, 1.5f)
                close()
                moveTo(10.57f, 1.49f)
                lineToRelative(-7.9f, 4.16f)
                curveToRelative(-0.41f, 0.21f, -0.67f, 0.64f, -0.67f, 1.1f)
                curveTo(2.0f, 7.44f, 2.56f, 8.0f, 3.25f, 8.0f)
                horizontalLineToRelative(16.51f)
                curveTo(20.44f, 8.0f, 21.0f, 7.44f, 21.0f, 6.75f)
                curveToRelative(0.0f, -0.46f, -0.26f, -0.89f, -0.67f, -1.1f)
                lineToRelative(-7.9f, -4.16f)
                curveToRelative(-0.58f, -0.31f, -1.28f, -0.31f, -1.86f, 0.0f)
                close()
            }
        }.also { _accountBalance = it }
    }

private var _autoFixHigh: ImageVector? = null
val Icons.Rounded.AutoFixHigh: ImageVector
    get() {
        _autoFixHigh?.let { return it }
        return materialIcon(name = "Rounded.AutoFixHigh") {
            materialPath {
                moveTo(20.45f, 6.0f)
                lineToRelative(0.49f, -1.06f)
                lineTo(22.0f, 4.45f)
                curveToRelative(0.39f, -0.18f, 0.39f, -0.73f, 0.0f, -0.91f)
                lineToRelative(-1.06f, -0.49f)
                lineTo(20.45f, 2.0f)
                curveToRelative(-0.18f, -0.39f, -0.73f, -0.39f, -0.91f, 0.0f)
                lineToRelative(-0.49f, 1.06f)
                lineTo(18.0f, 3.55f)
                curveToRelative(-0.39f, 0.18f, -0.39f, 0.73f, 0.0f, 0.91f)
                lineToRelative(1.06f, 0.49f)
                lineTo(19.55f, 6.0f)
                curveTo(19.72f, 6.39f, 20.28f, 6.39f, 20.45f, 6.0f)
                close()
            }
            materialPath {
                moveTo(8.95f, 6.0f)
                lineToRelative(0.49f, -1.06f)
                lineToRelative(1.06f, -0.49f)
                curveToRelative(0.39f, -0.18f, 0.39f, -0.73f, 0.0f, -0.91f)
                lineTo(9.44f, 3.06f)
                lineTo(8.95f, 2.0f)
                curveTo(8.78f, 1.61f, 8.22f, 1.61f, 8.05f, 2.0f)
                lineTo(7.56f, 3.06f)
                lineTo(6.5f, 3.55f)
                curveToRelative(-0.39f, 0.18f, -0.39f, 0.73f, 0.0f, 0.91f)
                lineToRelative(1.06f, 0.49f)
                lineTo(8.05f, 6.0f)
                curveTo(8.22f, 6.39f, 8.78f, 6.39f, 8.95f, 6.0f)
                close()
            }
            materialPath {
                moveTo(19.55f, 13.5f)
                lineToRelative(-0.49f, 1.06f)
                lineTo(18.0f, 15.05f)
                curveToRelative(-0.39f, 0.18f, -0.39f, 0.73f, 0.0f, 0.91f)
                lineToRelative(1.06f, 0.49f)
                lineToRelative(0.49f, 1.06f)
                curveToRelative(0.18f, 0.39f, 0.73f, 0.39f, 0.91f, 0.0f)
                lineToRelative(0.49f, -1.06f)
                lineTo(22.0f, 15.95f)
                curveToRelative(0.39f, -0.18f, 0.39f, -0.73f, 0.0f, -0.91f)
                lineToRelative(-1.06f, -0.49f)
                lineToRelative(-0.49f, -1.06f)
                curveTo(20.28f, 13.11f, 19.72f, 13.11f, 19.55f, 13.5f)
                close()
            }
            materialPath {
                moveTo(17.71f, 9.12f)
                lineToRelative(-2.83f, -2.83f)
                curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0.0f)
                lineTo(2.29f, 17.46f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0.0f, 1.41f)
                lineToRelative(2.83f, 2.83f)
                curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0.0f)
                lineToRelative(11.17f, -11.17f)
                curveTo(18.1f, 10.15f, 18.1f, 9.51f, 17.71f, 9.12f)
                close()
                moveTo(14.21f, 11.21f)
                lineToRelative(-1.41f, -1.41f)
                lineToRelative(1.38f, -1.38f)
                lineToRelative(1.41f, 1.41f)
                lineTo(14.21f, 11.21f)
                close()
            }
        }.also { _autoFixHigh = it }
    }

private var _autoStories: ImageVector? = null
val Icons.Rounded.AutoStories: ImageVector
    get() {
        _autoStories?.let { return it }
        return materialIcon(name = "Rounded.AutoStories") {
            materialPath {
                moveTo(18.15f, 1.35f)
                lineToRelative(-4.0f, 4.0f)
                curveTo(14.05f, 5.45f, 14.0f, 5.57f, 14.0f, 5.71f)
                verticalLineToRelative(8.17f)
                curveToRelative(0.0f, 0.43f, 0.51f, 0.66f, 0.83f, 0.37f)
                lineToRelative(4.0f, -3.6f)
                curveToRelative(0.11f, -0.09f, 0.17f, -0.23f, 0.17f, -0.37f)
                verticalLineTo(1.71f)
                curveTo(19.0f, 1.26f, 18.46f, 1.04f, 18.15f, 1.35f)
                close()
                moveTo(22.47f, 5.2f)
                curveTo(22.0f, 4.96f, 21.51f, 4.76f, 21.0f, 4.59f)
                verticalLineToRelative(12.03f)
                curveTo(19.86f, 16.21f, 18.69f, 16.0f, 17.5f, 16.0f)
                curveToRelative(-1.9f, 0.0f, -3.78f, 0.54f, -5.5f, 1.58f)
                verticalLineTo(5.48f)
                curveTo(10.38f, 4.55f, 8.51f, 4.0f, 6.5f, 4.0f)
                curveTo(4.71f, 4.0f, 3.02f, 4.44f, 1.53f, 5.2f)
                curveTo(1.2f, 5.36f, 1.0f, 5.71f, 1.0f, 6.08f)
                verticalLineToRelative(12.08f)
                curveToRelative(0.0f, 0.76f, 0.81f, 1.23f, 1.48f, 0.87f)
                curveTo(3.69f, 18.4f, 5.05f, 18.0f, 6.5f, 18.0f)
                curveToRelative(2.07f, 0.0f, 3.98f, 0.82f, 5.5f, 2.0f)
                curveToRelative(1.52f, -1.18f, 3.43f, -2.0f, 5.5f, -2.0f)
                curveToRelative(1.45f, 0.0f, 2.81f, 0.4f, 4.02f, 1.04f)
                curveTo(22.19f, 19.4f, 23.0f, 18.93f, 23.0f, 18.17f)
                verticalLineTo(6.08f)
                curveTo(23.0f, 5.71f, 22.8f, 5.36f, 22.47f, 5.2f)
                close()
            }
        }.also { _autoStories = it }
    }

private var _block: ImageVector? = null
val Icons.Rounded.Block: ImageVector
    get() {
        _block?.let { return it }
        return materialIcon(name = "Rounded.Block") {
            materialPath {
                moveTo(12.0f, 2.0f)
                curveTo(6.48f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                reflectiveCurveToRelative(4.48f, 10.0f, 10.0f, 10.0f)
                reflectiveCurveToRelative(10.0f, -4.48f, 10.0f, -10.0f)
                reflectiveCurveTo(17.52f, 2.0f, 12.0f, 2.0f)
                close()
                moveTo(4.0f, 12.0f)
                curveToRelative(0.0f, -4.42f, 3.58f, -8.0f, 8.0f, -8.0f)
                curveToRelative(1.85f, 0.0f, 3.55f, 0.63f, 4.9f, 1.69f)
                lineTo(5.69f, 16.9f)
                curveTo(4.63f, 15.55f, 4.0f, 13.85f, 4.0f, 12.0f)
                close()
                moveTo(12.0f, 20.0f)
                curveToRelative(-1.85f, 0.0f, -3.55f, -0.63f, -4.9f, -1.69f)
                lineTo(18.31f, 7.1f)
                curveTo(19.37f, 8.45f, 20.0f, 10.15f, 20.0f, 12.0f)
                curveToRelative(0.0f, 4.42f, -3.58f, 8.0f, -8.0f, 8.0f)
                close()
            }
        }.also { _block = it }
    }

private var _bolt: ImageVector? = null
val Icons.Rounded.Bolt: ImageVector
    get() {
        _bolt?.let { return it }
        return materialIcon(name = "Rounded.Bolt") {
            materialPath {
                moveTo(10.67f, 21.0f)
                lineTo(10.67f, 21.0f)
                curveToRelative(-0.35f, 0.0f, -0.62f, -0.31f, -0.57f, -0.66f)
                lineTo(11.0f, 14.0f)
                horizontalLineTo(7.5f)
                curveToRelative(-0.88f, 0.0f, -0.33f, -0.75f, -0.31f, -0.78f)
                curveToRelative(1.26f, -2.23f, 3.15f, -5.53f, 5.65f, -9.93f)
                curveToRelative(0.1f, -0.18f, 0.3f, -0.29f, 0.5f, -0.29f)
                horizontalLineToRelative(0.0f)
                curveToRelative(0.35f, 0.0f, 0.62f, 0.31f, 0.57f, 0.66f)
                lineTo(13.01f, 10.0f)
                horizontalLineToRelative(3.51f)
                curveToRelative(0.4f, 0.0f, 0.62f, 0.19f, 0.4f, 0.66f)
                curveToRelative(-3.29f, 5.74f, -5.2f, 9.09f, -5.75f, 10.05f)
                curveTo(11.07f, 20.89f, 10.88f, 21.0f, 10.67f, 21.0f)
                close()
            }
        }.also { _bolt = it }
    }

private var _bookmark: ImageVector? = null
val Icons.Rounded.Bookmark: ImageVector
    get() {
        _bookmark?.let { return it }
        return materialIcon(name = "Rounded.Bookmark") {
            materialPath {
                moveTo(17.0f, 3.0f)
                horizontalLineTo(7.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(16.0f)
                lineToRelative(7.0f, -3.0f)
                lineToRelative(7.0f, 3.0f)
                verticalLineTo(5.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
            }
        }.also { _bookmark = it }
    }

private var _bookmarkBorder: ImageVector? = null
val Icons.Rounded.BookmarkBorder: ImageVector
    get() {
        _bookmarkBorder?.let { return it }
        return materialIcon(name = "Rounded.BookmarkBorder") {
            materialPath {
                moveTo(17.0f, 3.0f)
                lineTo(7.0f, 3.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(16.0f)
                lineToRelative(7.0f, -3.0f)
                lineToRelative(7.0f, 3.0f)
                lineTo(19.0f, 5.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(17.0f, 18.0f)
                lineToRelative(-5.0f, -2.18f)
                lineTo(7.0f, 18.0f)
                lineTo(7.0f, 6.0f)
                curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
                horizontalLineToRelative(8.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
                verticalLineToRelative(12.0f)
                close()
            }
        }.also { _bookmarkBorder = it }
    }

private var _bookmarks: ImageVector? = null
val Icons.Rounded.Bookmarks: ImageVector
    get() {
        _bookmarks?.let { return it }
        return materialIcon(name = "Rounded.Bookmarks") {
            materialPath {
                moveTo(19.0f, 18.0f)
                lineToRelative(2.0f, 1.0f)
                verticalLineTo(3.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                horizontalLineTo(8.99f)
                curveTo(7.89f, 1.0f, 7.0f, 1.9f, 7.0f, 3.0f)
                horizontalLineToRelative(10.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, 0.9f, 2.0f, 2.0f)
                verticalLineToRelative(13.0f)
                close()
                moveTo(15.0f, 5.0f)
                horizontalLineTo(5.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(16.0f)
                lineToRelative(7.0f, -3.0f)
                lineToRelative(7.0f, 3.0f)
                verticalLineTo(7.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
            }
        }.also { _bookmarks = it }
    }

private var _brightnessAuto: ImageVector? = null
val Icons.Rounded.BrightnessAuto: ImageVector
    get() {
        _brightnessAuto?.let { return it }
        return materialIcon(name = "Rounded.BrightnessAuto") {
            materialPath {
                moveTo(10.85f, 12.65f)
                horizontalLineToRelative(2.3f)
                lineTo(12.0f, 9.0f)
                lineToRelative(-1.15f, 3.65f)
                close()
                moveTo(20.0f, 8.69f)
                lineTo(20.0f, 6.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                horizontalLineToRelative(-2.69f)
                lineToRelative(-1.9f, -1.9f)
                curveToRelative(-0.78f, -0.78f, -2.05f, -0.78f, -2.83f, 0.0f)
                lineTo(8.69f, 4.0f)
                lineTo(6.0f, 4.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(2.69f)
                lineToRelative(-1.9f, 1.9f)
                curveToRelative(-0.78f, 0.78f, -0.78f, 2.05f, 0.0f, 2.83f)
                lineToRelative(1.9f, 1.9f)
                lineTo(4.0f, 18.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(2.69f)
                lineToRelative(1.9f, 1.9f)
                curveToRelative(0.78f, 0.78f, 2.05f, 0.78f, 2.83f, 0.0f)
                lineToRelative(1.9f, -1.9f)
                lineTo(18.0f, 20.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineToRelative(-2.69f)
                lineToRelative(1.9f, -1.9f)
                curveToRelative(0.78f, -0.78f, 0.78f, -2.05f, 0.0f, -2.83f)
                lineTo(20.0f, 8.69f)
                close()
                moveTo(14.09f, 15.4f)
                lineTo(13.6f, 14.0f)
                horizontalLineToRelative(-3.2f)
                lineToRelative(-0.49f, 1.4f)
                curveToRelative(-0.13f, 0.36f, -0.46f, 0.6f, -0.84f, 0.6f)
                curveToRelative(-0.62f, 0.0f, -1.05f, -0.61f, -0.84f, -1.19f)
                lineToRelative(2.44f, -6.86f)
                curveToRelative(0.2f, -0.57f, 0.73f, -0.95f, 1.33f, -0.95f)
                curveToRelative(0.6f, 0.0f, 1.13f, 0.38f, 1.34f, 0.94f)
                lineToRelative(2.44f, 6.86f)
                curveToRelative(0.21f, 0.58f, -0.22f, 1.19f, -0.84f, 1.19f)
                curveToRelative(-0.39f, 0.01f, -0.72f, -0.23f, -0.85f, -0.59f)
                close()
            }
        }.also { _brightnessAuto = it }
    }

private var _cameraAlt: ImageVector? = null
val Icons.Rounded.CameraAlt: ImageVector
    get() {
        _cameraAlt?.let { return it }
        return materialIcon(name = "Rounded.CameraAlt") {
            materialPath {
                moveTo(12.0f, 12.0f)
                moveToRelative(-3.0f, 0.0f)
                arcToRelative(3.0f, 3.0f, 0.0f, true, true, 6.0f, 0.0f)
                arcToRelative(3.0f, 3.0f, 0.0f, true, true, -6.0f, 0.0f)
            }
            materialPath {
                moveTo(20.0f, 4.0f)
                horizontalLineToRelative(-3.17f)
                lineToRelative(-1.24f, -1.35f)
                curveToRelative(-0.37f, -0.41f, -0.91f, -0.65f, -1.47f, -0.65f)
                lineTo(9.88f, 2.0f)
                curveToRelative(-0.56f, 0.0f, -1.1f, 0.24f, -1.48f, 0.65f)
                lineTo(7.17f, 4.0f)
                lineTo(4.0f, 4.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(12.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(16.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                lineTo(22.0f, 6.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(12.0f, 17.0f)
                curveToRelative(-2.76f, 0.0f, -5.0f, -2.24f, -5.0f, -5.0f)
                reflectiveCurveToRelative(2.24f, -5.0f, 5.0f, -5.0f)
                reflectiveCurveToRelative(5.0f, 2.24f, 5.0f, 5.0f)
                reflectiveCurveToRelative(-2.24f, 5.0f, -5.0f, 5.0f)
                close()
            }
        }.also { _cameraAlt = it }
    }

private var _chevronLeft: ImageVector? = null
val Icons.Rounded.ChevronLeft: ImageVector
    get() {
        _chevronLeft?.let { return it }
        return materialIcon(name = "Rounded.ChevronLeft") {
            materialPath {
                moveTo(14.71f, 6.71f)
                curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0.0f)
                lineTo(8.71f, 11.3f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0.0f, 1.41f)
                lineToRelative(4.59f, 4.59f)
                curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0.0f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0.0f, -1.41f)
                lineTo(10.83f, 12.0f)
                lineToRelative(3.88f, -3.88f)
                curveToRelative(0.39f, -0.39f, 0.38f, -1.03f, 0.0f, -1.41f)
                close()
            }
        }.also { _chevronLeft = it }
    }

private var _chevronRight: ImageVector? = null
val Icons.Rounded.ChevronRight: ImageVector
    get() {
        _chevronRight?.let { return it }
        return materialIcon(name = "Rounded.ChevronRight") {
            materialPath {
                moveTo(9.29f, 6.71f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0.0f, 1.41f)
                lineTo(13.17f, 12.0f)
                lineToRelative(-3.88f, 3.88f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0.0f, 1.41f)
                curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0.0f)
                lineToRelative(4.59f, -4.59f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0.0f, -1.41f)
                lineTo(10.7f, 6.7f)
                curveToRelative(-0.38f, -0.38f, -1.02f, -0.38f, -1.41f, 0.01f)
                close()
            }
        }.also { _chevronRight = it }
    }

private var _clearAll: ImageVector? = null
val Icons.Rounded.ClearAll: ImageVector
    get() {
        _clearAll?.let { return it }
        return materialIcon(name = "Rounded.ClearAll") {
            materialPath {
                moveTo(6.0f, 13.0f)
                horizontalLineToRelative(12.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                reflectiveCurveToRelative(-0.45f, -1.0f, -1.0f, -1.0f)
                lineTo(6.0f, 11.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                reflectiveCurveToRelative(0.45f, 1.0f, 1.0f, 1.0f)
                close()
                moveTo(4.0f, 17.0f)
                horizontalLineToRelative(12.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                reflectiveCurveToRelative(-0.45f, -1.0f, -1.0f, -1.0f)
                lineTo(4.0f, 15.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                reflectiveCurveToRelative(0.45f, 1.0f, 1.0f, 1.0f)
                close()
                moveTo(7.0f, 8.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(12.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                reflectiveCurveToRelative(-0.45f, -1.0f, -1.0f, -1.0f)
                lineTo(8.0f, 7.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                close()
            }
        }.also { _clearAll = it }
    }

private var _cloud: ImageVector? = null
val Icons.Rounded.Cloud: ImageVector
    get() {
        _cloud?.let { return it }
        return materialIcon(name = "Rounded.Cloud") {
            materialPath {
                moveTo(19.35f, 10.04f)
                curveTo(18.67f, 6.59f, 15.64f, 4.0f, 12.0f, 4.0f)
                curveTo(9.11f, 4.0f, 6.6f, 5.64f, 5.35f, 8.04f)
                curveTo(2.34f, 8.36f, 0.0f, 10.91f, 0.0f, 14.0f)
                curveToRelative(0.0f, 3.31f, 2.69f, 6.0f, 6.0f, 6.0f)
                horizontalLineToRelative(13.0f)
                curveToRelative(2.76f, 0.0f, 5.0f, -2.24f, 5.0f, -5.0f)
                curveToRelative(0.0f, -2.64f, -2.05f, -4.78f, -4.65f, -4.96f)
                close()
            }
        }.also { _cloud = it }
    }

private var _code: ImageVector? = null
val Icons.Rounded.Code: ImageVector
    get() {
        _code?.let { return it }
        return materialIcon(name = "Rounded.Code") {
            materialPath {
                moveTo(8.7f, 15.9f)
                lineTo(4.8f, 12.0f)
                lineToRelative(3.9f, -3.9f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.01f, 0.0f, -1.4f)
                curveToRelative(-0.39f, -0.39f, -1.01f, -0.39f, -1.4f, 0.0f)
                lineToRelative(-4.59f, 4.59f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0.0f, 1.41f)
                lineToRelative(4.59f, 4.6f)
                curveToRelative(0.39f, 0.39f, 1.01f, 0.39f, 1.4f, 0.0f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.01f, 0.0f, -1.4f)
                close()
                moveTo(15.3f, 15.9f)
                lineToRelative(3.9f, -3.9f)
                lineToRelative(-3.9f, -3.9f)
                curveToRelative(-0.39f, -0.39f, -0.39f, -1.01f, 0.0f, -1.4f)
                curveToRelative(0.39f, -0.39f, 1.01f, -0.39f, 1.4f, 0.0f)
                lineToRelative(4.59f, 4.59f)
                curveToRelative(0.39f, 0.39f, 0.39f, 1.02f, 0.0f, 1.41f)
                lineToRelative(-4.59f, 4.6f)
                curveToRelative(-0.39f, 0.39f, -1.01f, 0.39f, -1.4f, 0.0f)
                curveToRelative(-0.39f, -0.39f, -0.39f, -1.01f, 0.0f, -1.4f)
                close()
            }
        }.also { _code = it }
    }

private var _computer: ImageVector? = null
val Icons.Rounded.Computer: ImageVector
    get() {
        _computer?.let { return it }
        return materialIcon(name = "Rounded.Computer") {
            materialPath {
                moveTo(20.0f, 18.0f)
                curveToRelative(1.1f, 0.0f, 1.99f, -0.9f, 1.99f, -2.0f)
                lineTo(22.0f, 6.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                horizontalLineTo(4.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(10.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineTo(1.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                reflectiveCurveToRelative(0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(22.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                reflectiveCurveToRelative(-0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineToRelative(-3.0f)
                close()
                moveTo(5.0f, 6.0f)
                horizontalLineToRelative(14.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
                verticalLineToRelative(8.0f)
                curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
                horizontalLineTo(5.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                verticalLineTo(7.0f)
                curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
                close()
            }
        }.also { _computer = it }
    }

private var _contentCopy: ImageVector? = null
val Icons.Rounded.ContentCopy: ImageVector
    get() {
        _contentCopy?.let { return it }
        return materialIcon(name = "Rounded.ContentCopy") {
            materialPath {
                moveTo(15.0f, 20.0f)
                horizontalLineTo(5.0f)
                verticalLineTo(7.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineToRelative(0.0f)
                curveTo(3.45f, 6.0f, 3.0f, 6.45f, 3.0f, 7.0f)
                verticalLineToRelative(13.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(10.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineToRelative(0.0f)
                curveTo(16.0f, 20.45f, 15.55f, 20.0f, 15.0f, 20.0f)
                close()
                moveTo(20.0f, 16.0f)
                verticalLineTo(4.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                horizontalLineTo(9.0f)
                curveTo(7.9f, 2.0f, 7.0f, 2.9f, 7.0f, 4.0f)
                verticalLineToRelative(12.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(9.0f)
                curveTo(19.1f, 18.0f, 20.0f, 17.1f, 20.0f, 16.0f)
                close()
                moveTo(18.0f, 16.0f)
                horizontalLineTo(9.0f)
                verticalLineTo(4.0f)
                horizontalLineToRelative(9.0f)
                verticalLineTo(16.0f)
                close()
            }
        }.also { _contentCopy = it }
    }

private var _darkMode: ImageVector? = null
val Icons.Rounded.DarkMode: ImageVector
    get() {
        _darkMode?.let { return it }
        return materialIcon(name = "Rounded.DarkMode") {
            materialPath {
                moveTo(11.01f, 3.05f)
                curveTo(6.51f, 3.54f, 3.0f, 7.36f, 3.0f, 12.0f)
                curveToRelative(0.0f, 4.97f, 4.03f, 9.0f, 9.0f, 9.0f)
                curveToRelative(4.63f, 0.0f, 8.45f, -3.5f, 8.95f, -8.0f)
                curveToRelative(0.09f, -0.79f, -0.78f, -1.42f, -1.54f, -0.95f)
                curveToRelative(-0.84f, 0.54f, -1.84f, 0.85f, -2.91f, 0.85f)
                curveToRelative(-2.98f, 0.0f, -5.4f, -2.42f, -5.4f, -5.4f)
                curveToRelative(0.0f, -1.06f, 0.31f, -2.06f, 0.84f, -2.89f)
                curveTo(12.39f, 3.94f, 11.9f, 2.98f, 11.01f, 3.05f)
                close()
            }
        }.also { _darkMode = it }
    }

private var _dashboardCustomize: ImageVector? = null
val Icons.Rounded.DashboardCustomize: ImageVector
    get() {
        _dashboardCustomize?.let { return it }
        return materialIcon(name = "Rounded.DashboardCustomize") {
            materialPath {
                moveTo(4.0f, 3.0f)
                horizontalLineToRelative(6.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
                verticalLineToRelative(6.0f)
                curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
                horizontalLineTo(4.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                verticalLineTo(4.0f)
                curveTo(3.0f, 3.45f, 3.45f, 3.0f, 4.0f, 3.0f)
                close()
                moveTo(14.0f, 3.0f)
                horizontalLineToRelative(6.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
                verticalLineToRelative(6.0f)
                curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
                horizontalLineToRelative(-6.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                verticalLineTo(4.0f)
                curveTo(13.0f, 3.45f, 13.45f, 3.0f, 14.0f, 3.0f)
                close()
                moveTo(4.0f, 13.0f)
                horizontalLineToRelative(6.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
                verticalLineToRelative(6.0f)
                curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
                horizontalLineTo(4.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                verticalLineToRelative(-6.0f)
                curveTo(3.0f, 13.45f, 3.45f, 13.0f, 4.0f, 13.0f)
                close()
                moveTo(17.0f, 13.0f)
                lineTo(17.0f, 13.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(-2.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                verticalLineToRelative(0.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(2.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(0.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(2.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineToRelative(0.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(-2.0f)
                curveTo(18.0f, 13.45f, 17.55f, 13.0f, 17.0f, 13.0f)
                close()
            }
        }.also { _dashboardCustomize = it }
    }

private var _deleteOutline: ImageVector? = null
val Icons.Rounded.DeleteOutline: ImageVector
    get() {
        _deleteOutline?.let { return it }
        return materialIcon(name = "Rounded.DeleteOutline") {
            materialPath {
                moveTo(6.0f, 19.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(8.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                lineTo(18.0f, 9.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                lineTo(8.0f, 7.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(10.0f)
                close()
                moveTo(9.0f, 9.0f)
                horizontalLineToRelative(6.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
                verticalLineToRelative(8.0f)
                curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
                lineTo(9.0f, 19.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                verticalLineToRelative(-8.0f)
                curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
                close()
                moveTo(15.5f, 4.0f)
                lineToRelative(-0.71f, -0.71f)
                curveToRelative(-0.18f, -0.18f, -0.44f, -0.29f, -0.7f, -0.29f)
                lineTo(9.91f, 3.0f)
                curveToRelative(-0.26f, 0.0f, -0.52f, 0.11f, -0.7f, 0.29f)
                lineTo(8.5f, 4.0f)
                lineTo(6.0f, 4.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                reflectiveCurveToRelative(0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(12.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                reflectiveCurveToRelative(-0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineToRelative(-2.5f)
                close()
            }
        }.also { _deleteOutline = it }
    }

private var _deleteSweep: ImageVector? = null
val Icons.Rounded.DeleteSweep: ImageVector
    get() {
        _deleteSweep?.let { return it }
        return materialIcon(name = "Rounded.DeleteSweep") {
            materialPath {
                moveTo(16.0f, 16.0f)
                horizontalLineToRelative(2.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
                reflectiveCurveToRelative(-0.45f, 1.0f, -1.0f, 1.0f)
                horizontalLineToRelative(-2.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                reflectiveCurveToRelative(0.45f, -1.0f, 1.0f, -1.0f)
                close()
                moveTo(16.0f, 8.0f)
                horizontalLineToRelative(5.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
                reflectiveCurveToRelative(-0.45f, 1.0f, -1.0f, 1.0f)
                horizontalLineToRelative(-5.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                reflectiveCurveToRelative(0.45f, -1.0f, 1.0f, -1.0f)
                close()
                moveTo(16.0f, 12.0f)
                horizontalLineToRelative(4.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
                reflectiveCurveToRelative(-0.45f, 1.0f, -1.0f, 1.0f)
                horizontalLineToRelative(-4.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                reflectiveCurveToRelative(0.45f, -1.0f, 1.0f, -1.0f)
                close()
                moveTo(3.0f, 18.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(6.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                lineTo(13.0f, 8.0f)
                lineTo(3.0f, 8.0f)
                verticalLineToRelative(10.0f)
                close()
                moveTo(13.0f, 5.0f)
                horizontalLineToRelative(-2.0f)
                lineToRelative(-0.71f, -0.71f)
                curveToRelative(-0.18f, -0.18f, -0.44f, -0.29f, -0.7f, -0.29f)
                lineTo(6.41f, 4.0f)
                curveToRelative(-0.26f, 0.0f, -0.52f, 0.11f, -0.7f, 0.29f)
                lineTo(5.0f, 5.0f)
                lineTo(3.0f, 5.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                reflectiveCurveToRelative(0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(10.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                reflectiveCurveToRelative(-0.45f, -1.0f, -1.0f, -1.0f)
                close()
            }
        }.also { _deleteSweep = it }
    }

private var _desktopWindows: ImageVector? = null
val Icons.Rounded.DesktopWindows: ImageVector
    get() {
        _desktopWindows?.let { return it }
        return materialIcon(name = "Rounded.DesktopWindows") {
            materialPath {
                moveTo(21.0f, 2.0f)
                lineTo(3.0f, 2.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(12.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(7.0f)
                verticalLineToRelative(2.0f)
                lineTo(9.0f, 20.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                reflectiveCurveToRelative(0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(6.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                reflectiveCurveToRelative(-0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineToRelative(-1.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(7.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                lineTo(23.0f, 4.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(20.0f, 16.0f)
                lineTo(4.0f, 16.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                lineTo(3.0f, 5.0f)
                curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
                horizontalLineToRelative(16.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
                verticalLineToRelative(10.0f)
                curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
                close()
            }
        }.also { _desktopWindows = it }
    }

private var _devices: ImageVector? = null
val Icons.Rounded.Devices: ImageVector
    get() {
        _devices?.let { return it }
        return materialIcon(name = "Rounded.Devices") {
            materialPath {
                moveTo(4.0f, 7.0f)
                curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
                horizontalLineToRelative(16.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                reflectiveCurveToRelative(-0.45f, -1.0f, -1.0f, -1.0f)
                lineTo(4.0f, 4.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(11.0f)
                horizontalLineToRelative(-0.5f)
                curveToRelative(-0.83f, 0.0f, -1.5f, 0.67f, -1.5f, 1.5f)
                reflectiveCurveTo(0.67f, 20.0f, 1.5f, 20.0f)
                lineTo(14.0f, 20.0f)
                verticalLineToRelative(-3.0f)
                lineTo(4.0f, 17.0f)
                lineTo(4.0f, 7.0f)
                close()
                moveTo(23.0f, 8.0f)
                horizontalLineToRelative(-6.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                verticalLineToRelative(10.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(6.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                lineTo(24.0f, 9.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                close()
                moveTo(22.0f, 17.0f)
                horizontalLineToRelative(-4.0f)
                verticalLineToRelative(-7.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(7.0f)
                close()
            }
        }.also { _devices = it }
    }

private var _dns: ImageVector? = null
val Icons.Rounded.Dns: ImageVector
    get() {
        _dns?.let { return it }
        return materialIcon(name = "Rounded.Dns") {
            materialPath {
                moveTo(19.0f, 13.0f)
                horizontalLineTo(5.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(4.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(14.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineToRelative(-4.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(7.0f, 19.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, -0.9f, -2.0f, -2.0f)
                reflectiveCurveToRelative(0.9f, -2.0f, 2.0f, -2.0f)
                reflectiveCurveToRelative(2.0f, 0.9f, 2.0f, 2.0f)
                reflectiveCurveToRelative(-0.9f, 2.0f, -2.0f, 2.0f)
                close()
                moveTo(19.0f, 3.0f)
                horizontalLineTo(5.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(4.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(14.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(5.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(7.0f, 9.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, -0.9f, -2.0f, -2.0f)
                reflectiveCurveToRelative(0.9f, -2.0f, 2.0f, -2.0f)
                reflectiveCurveToRelative(2.0f, 0.9f, 2.0f, 2.0f)
                reflectiveCurveToRelative(-0.9f, 2.0f, -2.0f, 2.0f)
                close()
            }
        }.also { _dns = it }
    }

private var _download: ImageVector? = null
val Icons.Rounded.Download: ImageVector
    get() {
        _download?.let { return it }
        return materialIcon(name = "Rounded.Download") {
            materialPath {
                moveTo(16.59f, 9.0f)
                horizontalLineTo(15.0f)
                verticalLineTo(4.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineToRelative(-4.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                verticalLineToRelative(5.0f)
                horizontalLineTo(7.41f)
                curveToRelative(-0.89f, 0.0f, -1.34f, 1.08f, -0.71f, 1.71f)
                lineToRelative(4.59f, 4.59f)
                curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0.0f)
                lineToRelative(4.59f, -4.59f)
                curveToRelative(0.63f, -0.63f, 0.19f, -1.71f, -0.7f, -1.71f)
                close()
                moveTo(5.0f, 19.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(12.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                reflectiveCurveToRelative(-0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineTo(6.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                close()
            }
        }.also { _download = it }
    }

private var _downloadDone: ImageVector? = null
val Icons.Rounded.DownloadDone: ImageVector
    get() {
        _downloadDone?.let { return it }
        return materialIcon(name = "Rounded.DownloadDone") {
            materialPath {
                moveTo(6.0f, 18.0f)
                horizontalLineToRelative(12.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
                reflectiveCurveToRelative(-0.45f, 1.0f, -1.0f, 1.0f)
                lineTo(6.0f, 20.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                reflectiveCurveToRelative(0.45f, -1.0f, 1.0f, -1.0f)
                close()
                moveTo(11.01f, 13.9f)
                curveToRelative(-0.78f, 0.77f, -2.04f, 0.77f, -2.82f, -0.01f)
                lineTo(6.0f, 11.7f)
                curveToRelative(-0.55f, -0.55f, -0.54f, -1.44f, 0.03f, -1.97f)
                curveToRelative(0.54f, -0.52f, 1.4f, -0.5f, 1.92f, 0.02f)
                lineTo(9.6f, 11.4f)
                lineToRelative(6.43f, -6.43f)
                curveToRelative(0.54f, -0.54f, 1.41f, -0.54f, 1.95f, 0.0f)
                lineToRelative(0.04f, 0.04f)
                curveToRelative(0.54f, 0.54f, 0.54f, 1.42f, -0.01f, 1.96f)
                lineToRelative(-7.0f, 6.93f)
                close()
            }
        }.also { _downloadDone = it }
    }

private var _dragIndicator: ImageVector? = null
val Icons.Rounded.DragIndicator: ImageVector
    get() {
        _dragIndicator?.let { return it }
        return materialIcon(name = "Rounded.DragIndicator") {
            materialPath {
                moveTo(11.0f, 18.0f)
                curveToRelative(0.0f, 1.1f, -0.9f, 2.0f, -2.0f, 2.0f)
                reflectiveCurveToRelative(-2.0f, -0.9f, -2.0f, -2.0f)
                reflectiveCurveToRelative(0.9f, -2.0f, 2.0f, -2.0f)
                reflectiveCurveToRelative(2.0f, 0.9f, 2.0f, 2.0f)
                close()
                moveTo(9.0f, 10.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                reflectiveCurveToRelative(0.9f, 2.0f, 2.0f, 2.0f)
                reflectiveCurveToRelative(2.0f, -0.9f, 2.0f, -2.0f)
                reflectiveCurveToRelative(-0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(9.0f, 4.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                reflectiveCurveToRelative(0.9f, 2.0f, 2.0f, 2.0f)
                reflectiveCurveToRelative(2.0f, -0.9f, 2.0f, -2.0f)
                reflectiveCurveToRelative(-0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(15.0f, 8.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                reflectiveCurveToRelative(-0.9f, -2.0f, -2.0f, -2.0f)
                reflectiveCurveToRelative(-2.0f, 0.9f, -2.0f, 2.0f)
                reflectiveCurveToRelative(0.9f, 2.0f, 2.0f, 2.0f)
                close()
                moveTo(15.0f, 10.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                reflectiveCurveToRelative(0.9f, 2.0f, 2.0f, 2.0f)
                reflectiveCurveToRelative(2.0f, -0.9f, 2.0f, -2.0f)
                reflectiveCurveToRelative(-0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(15.0f, 16.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                reflectiveCurveToRelative(0.9f, 2.0f, 2.0f, 2.0f)
                reflectiveCurveToRelative(2.0f, -0.9f, 2.0f, -2.0f)
                reflectiveCurveToRelative(-0.9f, -2.0f, -2.0f, -2.0f)
                close()
            }
        }.also { _dragIndicator = it }
    }

private var _fileOpen: ImageVector? = null
val Icons.Rounded.FileOpen: ImageVector
    get() {
        _fileOpen?.let { return it }
        return materialIcon(name = "Rounded.FileOpen") {
            materialPath {
                moveTo(13.17f, 2.0f)
                horizontalLineTo(6.0f)
                curveTo(4.9f, 2.0f, 4.0f, 2.9f, 4.0f, 4.0f)
                verticalLineToRelative(16.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(9.0f)
                verticalLineToRelative(-6.0f)
                curveToRelative(0.0f, -1.1f, 0.9f, -2.0f, 2.0f, -2.0f)
                horizontalLineToRelative(3.0f)
                verticalLineTo(8.83f)
                curveToRelative(0.0f, -0.53f, -0.21f, -1.04f, -0.59f, -1.41f)
                lineToRelative(-4.83f, -4.83f)
                curveTo(14.21f, 2.21f, 13.7f, 2.0f, 13.17f, 2.0f)
                close()
                moveTo(13.0f, 8.0f)
                verticalLineTo(3.5f)
                lineTo(18.5f, 9.0f)
                horizontalLineTo(14.0f)
                curveTo(13.45f, 9.0f, 13.0f, 8.55f, 13.0f, 8.0f)
                close()
                moveTo(22.66f, 17.0f)
                curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
                horizontalLineToRelative(-1.24f)
                lineToRelative(2.24f, 2.24f)
                curveToRelative(0.39f, 0.39f, 0.39f, 1.02f, 0.0f, 1.41f)
                lineToRelative(0.0f, 0.0f)
                curveToRelative(-0.39f, 0.39f, -1.02f, 0.39f, -1.41f, 0.0f)
                lineTo(19.0f, 19.41f)
                lineToRelative(0.0f, 1.24f)
                curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                verticalLineTo(17.0f)
                curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
                horizontalLineToRelative(3.66f)
                curveTo(22.21f, 16.0f, 22.66f, 16.45f, 22.66f, 17.0f)
                close()
            }
        }.also { _fileOpen = it }
    }

private var _findInPage: ImageVector? = null
val Icons.Rounded.FindInPage: ImageVector
    get() {
        _findInPage?.let { return it }
        return materialIcon(name = "Rounded.FindInPage") {
            materialPath {
                moveTo(20.0f, 19.59f)
                verticalLineTo(8.83f)
                curveToRelative(0.0f, -0.53f, -0.21f, -1.04f, -0.59f, -1.41f)
                lineToRelative(-4.83f, -4.83f)
                curveToRelative(-0.37f, -0.38f, -0.88f, -0.59f, -1.41f, -0.59f)
                horizontalLineTo(6.0f)
                curveToRelative(-1.1f, 0.0f, -1.99f, 0.9f, -1.99f, 2.0f)
                lineTo(4.0f, 20.0f)
                curveToRelative(0.0f, 1.1f, 0.89f, 2.0f, 1.99f, 2.0f)
                horizontalLineTo(18.0f)
                curveToRelative(0.45f, 0.0f, 0.85f, -0.15f, 1.19f, -0.4f)
                lineToRelative(-4.43f, -4.43f)
                curveToRelative(-0.86f, 0.56f, -1.89f, 0.88f, -3.0f, 0.82f)
                curveToRelative(-2.37f, -0.11f, -4.4f, -1.96f, -4.72f, -4.31f)
                curveToRelative(-0.44f, -3.35f, 2.45f, -6.18f, 5.83f, -5.61f)
                curveToRelative(1.95f, 0.33f, 3.57f, 1.85f, 4.0f, 3.78f)
                curveToRelative(0.33f, 1.46f, 0.01f, 2.82f, -0.7f, 3.9f)
                lineTo(20.0f, 19.59f)
                close()
                moveTo(9.0f, 13.0f)
                curveToRelative(0.0f, 1.66f, 1.34f, 3.0f, 3.0f, 3.0f)
                reflectiveCurveToRelative(3.0f, -1.34f, 3.0f, -3.0f)
                reflectiveCurveToRelative(-1.34f, -3.0f, -3.0f, -3.0f)
                reflectiveCurveToRelative(-3.0f, 1.34f, -3.0f, 3.0f)
                close()
            }
        }.also { _findInPage = it }
    }

private var _fitnessCenter: ImageVector? = null
val Icons.Rounded.FitnessCenter: ImageVector
    get() {
        _fitnessCenter?.let { return it }
        return materialIcon(name = "Rounded.FitnessCenter") {
            materialPath {
                moveTo(20.57f, 14.86f)
                lineToRelative(0.72f, -0.72f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0.0f, -1.41f)
                lineToRelative(-0.02f, -0.02f)
                curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0.0f)
                lineTo(17.0f, 15.57f)
                lineTo(8.43f, 7.0f)
                lineToRelative(2.86f, -2.86f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0.0f, -1.41f)
                lineToRelative(-0.02f, -0.02f)
                curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0.0f)
                lineToRelative(-0.72f, 0.72f)
                lineToRelative(-0.72f, -0.72f)
                curveToRelative(-0.39f, -0.39f, -1.03f, -0.39f, -1.42f, 0.0f)
                lineTo(5.57f, 4.14f)
                lineToRelative(-0.72f, -0.72f)
                curveToRelative(-0.39f, -0.39f, -1.04f, -0.39f, -1.43f, 0.0f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.04f, 0.0f, 1.43f)
                lineToRelative(0.72f, 0.72f)
                lineTo(2.71f, 7.0f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0.0f, 1.41f)
                lineToRelative(0.72f, 0.72f)
                lineToRelative(-0.72f, 0.73f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0.0f, 1.41f)
                lineToRelative(0.02f, 0.02f)
                curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0.0f)
                lineTo(7.0f, 8.43f)
                lineTo(15.57f, 17.0f)
                lineToRelative(-2.86f, 2.86f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0.0f, 1.41f)
                lineToRelative(0.02f, 0.02f)
                curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0.0f)
                lineToRelative(0.72f, -0.72f)
                lineToRelative(0.72f, 0.72f)
                curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0.0f)
                lineToRelative(1.43f, -1.43f)
                lineToRelative(0.72f, 0.72f)
                curveToRelative(0.39f, 0.39f, 1.04f, 0.39f, 1.43f, 0.0f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.04f, 0.0f, -1.43f)
                lineToRelative(-0.72f, -0.72f)
                lineTo(21.29f, 17.0f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0.0f, -1.41f)
                lineToRelative(-0.72f, -0.73f)
                close()
            }
        }.also { _fitnessCenter = it }
    }

private var _flight: ImageVector? = null
val Icons.Rounded.Flight: ImageVector
    get() {
        _flight?.let { return it }
        return materialIcon(name = "Rounded.Flight") {
            materialPath {
                moveTo(21.0f, 14.58f)
                curveToRelative(0.0f, -0.36f, -0.19f, -0.69f, -0.49f, -0.89f)
                lineTo(13.0f, 9.0f)
                verticalLineTo(3.5f)
                curveToRelative(0.0f, -0.83f, -0.67f, -1.5f, -1.5f, -1.5f)
                reflectiveCurveTo(10.0f, 2.67f, 10.0f, 3.5f)
                verticalLineTo(9.0f)
                lineToRelative(-7.51f, 4.69f)
                curveToRelative(-0.3f, 0.19f, -0.49f, 0.53f, -0.49f, 0.89f)
                curveToRelative(0.0f, 0.7f, 0.68f, 1.21f, 1.36f, 1.0f)
                lineTo(10.0f, 13.5f)
                verticalLineTo(19.0f)
                lineToRelative(-1.8f, 1.35f)
                curveToRelative(-0.13f, 0.09f, -0.2f, 0.24f, -0.2f, 0.4f)
                verticalLineToRelative(0.59f)
                curveToRelative(0.0f, 0.33f, 0.32f, 0.57f, 0.64f, 0.48f)
                lineTo(11.5f, 21.0f)
                lineToRelative(2.86f, 0.82f)
                curveToRelative(0.32f, 0.09f, 0.64f, -0.15f, 0.64f, -0.48f)
                verticalLineToRelative(-0.59f)
                curveToRelative(0.0f, -0.16f, -0.07f, -0.31f, -0.2f, -0.4f)
                lineTo(13.0f, 19.0f)
                verticalLineToRelative(-5.5f)
                lineToRelative(6.64f, 2.08f)
                curveToRelative(0.68f, 0.21f, 1.36f, -0.3f, 1.36f, -1.0f)
                close()
            }
        }.also { _flight = it }
    }

private var _createNewFolder: ImageVector? = null
val Icons.Rounded.CreateNewFolder: ImageVector
    get() {
        _createNewFolder?.let { return it }
        return materialIcon(name = "Rounded.CreateNewFolder") {
            materialPath {
                moveTo(20.0f, 6.0f)
                horizontalLineToRelative(-8.0f)
                lineToRelative(-1.41f, -1.41f)
                curveTo(10.21f, 4.21f, 9.7f, 4.0f, 9.17f, 4.0f)
                horizontalLineTo(4.0f)
                curveTo(2.89f, 4.0f, 2.01f, 4.89f, 2.01f, 6.0f)
                lineTo(2.0f, 18.0f)
                curveToRelative(0.0f, 1.11f, 0.89f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(16.0f)
                curveToRelative(1.11f, 0.0f, 2.0f, -0.89f, 2.0f, -2.0f)
                verticalLineTo(8.0f)
                curveToRelative(0.0f, -1.11f, -0.89f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(18.0f, 14.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(2.0f)
                curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
                reflectiveCurveToRelative(-1.0f, -0.45f, -1.0f, -1.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(-2.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                reflectiveCurveToRelative(0.45f, -1.0f, 1.0f, -1.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-2.0f)
                curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
                reflectiveCurveToRelative(1.0f, 0.45f, 1.0f, 1.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(2.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
                reflectiveCurveToRelative(-0.45f, 1.0f, -1.0f, 1.0f)
                close()
            }
        }.also { _createNewFolder = it }
    }

private var _driveFileMove: ImageVector? = null
val Icons.Rounded.DriveFileMove: ImageVector
    get() {
        _driveFileMove?.let { return it }
        return materialIcon(name = "Rounded.DriveFileMove") {
            materialPath {
                moveTo(20.0f, 6.0f)
                horizontalLineToRelative(-8.0f)
                lineToRelative(-1.41f, -1.41f)
                curveTo(10.21f, 4.21f, 9.7f, 4.0f, 9.17f, 4.0f)
                horizontalLineTo(4.0f)
                curveTo(2.9f, 4.0f, 2.01f, 4.9f, 2.01f, 6.0f)
                lineTo(2.0f, 18.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(16.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(8.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(12.0f, 15.79f)
                verticalLineTo(14.0f)
                horizontalLineTo(9.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                reflectiveCurveToRelative(0.45f, -1.0f, 1.0f, -1.0f)
                horizontalLineToRelative(3.0f)
                verticalLineToRelative(-1.79f)
                curveToRelative(0.0f, -0.45f, 0.54f, -0.67f, 0.85f, -0.35f)
                lineToRelative(2.79f, 2.79f)
                curveToRelative(0.2f, 0.2f, 0.2f, 0.51f, 0.0f, 0.71f)
                lineToRelative(-2.79f, 2.79f)
                curveTo(12.54f, 16.46f, 12.0f, 16.24f, 12.0f, 15.79f)
                close()
            }
        }.also { _driveFileMove = it }
    }

private var _expandMore: ImageVector? = null
val Icons.Rounded.ExpandMore: ImageVector
    get() {
        _expandMore?.let { return it }
        return materialIcon(name = "Rounded.ExpandMore") {
            materialPath {
                moveTo(15.88f, 9.29f)
                lineTo(12.0f, 13.17f)
                lineTo(8.12f, 9.29f)
                curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0.0f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0.0f, 1.41f)
                lineToRelative(4.59f, 4.59f)
                curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0.0f)
                lineToRelative(4.59f, -4.59f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0.0f, -1.41f)
                curveToRelative(-0.39f, -0.38f, -1.03f, -0.39f, -1.42f, 0.0f)
                close()
            }
        }.also { _expandMore = it }
    }

private var _folder: ImageVector? = null
val Icons.Rounded.Folder: ImageVector
    get() {
        _folder?.let { return it }
        return materialIcon(name = "Rounded.Folder") {
            materialPath {
                moveTo(10.59f, 4.59f)
                curveTo(10.21f, 4.21f, 9.7f, 4.0f, 9.17f, 4.0f)
                horizontalLineTo(4.0f)
                curveToRelative(-1.1f, 0.0f, -1.99f, 0.9f, -1.99f, 2.0f)
                lineTo(2.0f, 18.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(16.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(8.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                horizontalLineToRelative(-8.0f)
                lineToRelative(-1.41f, -1.41f)
                close()
            }
        }.also { _folder = it }
    }

private var _folderOpen: ImageVector? = null
val Icons.Rounded.FolderOpen: ImageVector
    get() {
        _folderOpen?.let { return it }
        return materialIcon(name = "Rounded.FolderOpen") {
            materialPath {
                moveTo(20.0f, 6.0f)
                horizontalLineToRelative(-8.0f)
                lineToRelative(-1.41f, -1.41f)
                curveTo(10.21f, 4.21f, 9.7f, 4.0f, 9.17f, 4.0f)
                horizontalLineTo(4.0f)
                curveTo(2.9f, 4.0f, 2.01f, 4.9f, 2.01f, 6.0f)
                lineTo(2.0f, 18.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(16.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(8.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(19.0f, 18.0f)
                horizontalLineTo(5.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                verticalLineTo(9.0f)
                curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
                horizontalLineToRelative(14.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
                verticalLineToRelative(8.0f)
                curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
                close()
            }
        }.also { _folderOpen = it }
    }

private var _forum: ImageVector? = null
val Icons.Rounded.Forum: ImageVector
    get() {
        _forum?.let { return it }
        return materialIcon(name = "Rounded.Forum") {
            materialPath {
                moveTo(20.0f, 6.0f)
                horizontalLineToRelative(-1.0f)
                verticalLineToRelative(8.0f)
                curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
                lineTo(6.0f, 15.0f)
                verticalLineToRelative(1.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(10.0f)
                lineToRelative(4.0f, 4.0f)
                lineTo(22.0f, 8.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(17.0f, 11.0f)
                lineTo(17.0f, 4.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                lineTo(4.0f, 2.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(13.0f)
                lineToRelative(4.0f, -4.0f)
                horizontalLineToRelative(9.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                close()
            }
        }.also { _forum = it }
    }

private var _history: ImageVector? = null
val Icons.Rounded.History: ImageVector
    get() {
        _history?.let { return it }
        return materialIcon(name = "Rounded.History") {
            materialPath {
                moveTo(13.26f, 3.0f)
                curveTo(8.17f, 2.86f, 4.0f, 6.95f, 4.0f, 12.0f)
                lineTo(2.21f, 12.0f)
                curveToRelative(-0.45f, 0.0f, -0.67f, 0.54f, -0.35f, 0.85f)
                lineToRelative(2.79f, 2.8f)
                curveToRelative(0.2f, 0.2f, 0.51f, 0.2f, 0.71f, 0.0f)
                lineToRelative(2.79f, -2.8f)
                curveToRelative(0.31f, -0.31f, 0.09f, -0.85f, -0.36f, -0.85f)
                lineTo(6.0f, 12.0f)
                curveToRelative(0.0f, -3.9f, 3.18f, -7.05f, 7.1f, -7.0f)
                curveToRelative(3.72f, 0.05f, 6.85f, 3.18f, 6.9f, 6.9f)
                curveToRelative(0.05f, 3.91f, -3.1f, 7.1f, -7.0f, 7.1f)
                curveToRelative(-1.61f, 0.0f, -3.1f, -0.55f, -4.28f, -1.48f)
                curveToRelative(-0.4f, -0.31f, -0.96f, -0.28f, -1.32f, 0.08f)
                curveToRelative(-0.42f, 0.42f, -0.39f, 1.13f, 0.08f, 1.49f)
                curveTo(9.0f, 20.29f, 10.91f, 21.0f, 13.0f, 21.0f)
                curveToRelative(5.05f, 0.0f, 9.14f, -4.17f, 9.0f, -9.26f)
                curveToRelative(-0.13f, -4.69f, -4.05f, -8.61f, -8.74f, -8.74f)
                close()
                moveTo(12.75f, 8.0f)
                curveToRelative(-0.41f, 0.0f, -0.75f, 0.34f, -0.75f, 0.75f)
                verticalLineToRelative(3.68f)
                curveToRelative(0.0f, 0.35f, 0.19f, 0.68f, 0.49f, 0.86f)
                lineToRelative(3.12f, 1.85f)
                curveToRelative(0.36f, 0.21f, 0.82f, 0.09f, 1.03f, -0.26f)
                curveToRelative(0.21f, -0.36f, 0.09f, -0.82f, -0.26f, -1.03f)
                lineToRelative(-2.88f, -1.71f)
                verticalLineToRelative(-3.4f)
                curveToRelative(0.0f, -0.4f, -0.34f, -0.74f, -0.75f, -0.74f)
                close()
            }
        }.also { _history = it }
    }

private var _layers: ImageVector? = null
val Icons.Rounded.Layers: ImageVector
    get() {
        _layers?.let { return it }
        return materialIcon(name = "Rounded.Layers") {
            materialPath {
                moveTo(12.6f, 18.06f)
                curveToRelative(-0.36f, 0.28f, -0.87f, 0.28f, -1.23f, 0.0f)
                lineToRelative(-6.15f, -4.78f)
                curveToRelative(-0.36f, -0.28f, -0.86f, -0.28f, -1.22f, 0.0f)
                curveToRelative(-0.51f, 0.4f, -0.51f, 1.17f, 0.0f, 1.57f)
                lineToRelative(6.76f, 5.26f)
                curveToRelative(0.72f, 0.56f, 1.73f, 0.56f, 2.46f, 0.0f)
                lineToRelative(6.76f, -5.26f)
                curveToRelative(0.51f, -0.4f, 0.51f, -1.17f, 0.0f, -1.57f)
                lineToRelative(-0.01f, -0.01f)
                curveToRelative(-0.36f, -0.28f, -0.86f, -0.28f, -1.22f, 0.0f)
                lineToRelative(-6.15f, 4.79f)
                close()
                moveTo(13.23f, 15.04f)
                lineToRelative(6.76f, -5.26f)
                curveToRelative(0.51f, -0.4f, 0.51f, -1.18f, 0.0f, -1.58f)
                lineToRelative(-6.76f, -5.26f)
                curveToRelative(-0.72f, -0.56f, -1.73f, -0.56f, -2.46f, 0.0f)
                lineTo(4.01f, 8.21f)
                curveToRelative(-0.51f, 0.4f, -0.51f, 1.18f, 0.0f, 1.58f)
                lineToRelative(6.76f, 5.26f)
                curveToRelative(0.72f, 0.56f, 1.74f, 0.56f, 2.46f, -0.01f)
                close()
            }
        }.also { _layers = it }
    }

private var _layersClear: ImageVector? = null
val Icons.Rounded.LayersClear: ImageVector
    get() {
        _layersClear?.let { return it }
        return materialIcon(name = "Rounded.LayersClear") {
            materialPath {
                moveTo(19.99f, 9.79f)
                curveToRelative(0.51f, -0.4f, 0.51f, -1.18f, 0.0f, -1.58f)
                lineToRelative(-6.76f, -5.26f)
                curveToRelative(-0.72f, -0.56f, -1.73f, -0.56f, -2.46f, 0.0f)
                lineTo(9.41f, 4.02f)
                lineToRelative(7.88f, 7.88f)
                lineToRelative(2.7f, -2.11f)
                close()
                moveTo(19.99f, 13.28f)
                lineToRelative(-0.01f, -0.01f)
                curveToRelative(-0.36f, -0.28f, -0.86f, -0.28f, -1.22f, 0.0f)
                lineToRelative(-0.05f, 0.04f)
                lineToRelative(1.4f, 1.4f)
                curveToRelative(0.37f, -0.41f, 0.34f, -1.07f, -0.12f, -1.43f)
                close()
                moveTo(21.44f, 18.88f)
                lineTo(4.12f, 1.56f)
                curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0.0f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0.0f, 1.41f)
                lineToRelative(3.52f, 3.52f)
                lineToRelative(-2.22f, 1.72f)
                curveToRelative(-0.51f, 0.4f, -0.51f, 1.18f, 0.0f, 1.58f)
                lineToRelative(6.76f, 5.26f)
                curveToRelative(0.72f, 0.56f, 1.73f, 0.56f, 2.46f, 0.0f)
                lineToRelative(0.87f, -0.68f)
                lineToRelative(1.42f, 1.42f)
                lineToRelative(-2.92f, 2.27f)
                curveToRelative(-0.36f, 0.28f, -0.87f, 0.28f, -1.23f, 0.0f)
                lineToRelative(-6.15f, -4.78f)
                curveToRelative(-0.36f, -0.28f, -0.86f, -0.28f, -1.22f, 0.0f)
                curveToRelative(-0.51f, 0.4f, -0.51f, 1.17f, 0.0f, 1.57f)
                lineToRelative(6.76f, 5.26f)
                curveToRelative(0.72f, 0.56f, 1.73f, 0.56f, 2.46f, 0.0f)
                lineToRelative(3.72f, -2.89f)
                lineToRelative(3.07f, 3.07f)
                curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0.0f)
                curveToRelative(0.41f, -0.39f, 0.41f, -1.02f, 0.02f, -1.41f)
                close()
            }
        }.also { _layersClear = it }
    }

private var _lightMode: ImageVector? = null
val Icons.Rounded.LightMode: ImageVector
    get() {
        _lightMode?.let { return it }
        return materialIcon(name = "Rounded.LightMode") {
            materialPath {
                moveTo(12.0f, 7.0f)
                curveToRelative(-2.76f, 0.0f, -5.0f, 2.24f, -5.0f, 5.0f)
                reflectiveCurveToRelative(2.24f, 5.0f, 5.0f, 5.0f)
                reflectiveCurveToRelative(5.0f, -2.24f, 5.0f, -5.0f)
                reflectiveCurveTo(14.76f, 7.0f, 12.0f, 7.0f)
                lineTo(12.0f, 7.0f)
                close()
                moveTo(2.0f, 13.0f)
                lineToRelative(2.0f, 0.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                reflectiveCurveToRelative(-0.45f, -1.0f, -1.0f, -1.0f)
                lineToRelative(-2.0f, 0.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                reflectiveCurveTo(1.45f, 13.0f, 2.0f, 13.0f)
                close()
                moveTo(20.0f, 13.0f)
                lineToRelative(2.0f, 0.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                reflectiveCurveToRelative(-0.45f, -1.0f, -1.0f, -1.0f)
                lineToRelative(-2.0f, 0.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                reflectiveCurveTo(19.45f, 13.0f, 20.0f, 13.0f)
                close()
                moveTo(11.0f, 2.0f)
                verticalLineToRelative(2.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                reflectiveCurveToRelative(1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineTo(2.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                reflectiveCurveTo(11.0f, 1.45f, 11.0f, 2.0f)
                close()
                moveTo(11.0f, 20.0f)
                verticalLineToRelative(2.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                reflectiveCurveToRelative(1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineToRelative(-2.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                curveTo(11.45f, 19.0f, 11.0f, 19.45f, 11.0f, 20.0f)
                close()
                moveTo(5.99f, 4.58f)
                curveToRelative(-0.39f, -0.39f, -1.03f, -0.39f, -1.41f, 0.0f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.03f, 0.0f, 1.41f)
                lineToRelative(1.06f, 1.06f)
                curveToRelative(0.39f, 0.39f, 1.03f, 0.39f, 1.41f, 0.0f)
                reflectiveCurveToRelative(0.39f, -1.03f, 0.0f, -1.41f)
                lineTo(5.99f, 4.58f)
                close()
                moveTo(18.36f, 16.95f)
                curveToRelative(-0.39f, -0.39f, -1.03f, -0.39f, -1.41f, 0.0f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.03f, 0.0f, 1.41f)
                lineToRelative(1.06f, 1.06f)
                curveToRelative(0.39f, 0.39f, 1.03f, 0.39f, 1.41f, 0.0f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.03f, 0.0f, -1.41f)
                lineTo(18.36f, 16.95f)
                close()
                moveTo(19.42f, 5.99f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.03f, 0.0f, -1.41f)
                curveToRelative(-0.39f, -0.39f, -1.03f, -0.39f, -1.41f, 0.0f)
                lineToRelative(-1.06f, 1.06f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.03f, 0.0f, 1.41f)
                reflectiveCurveToRelative(1.03f, 0.39f, 1.41f, 0.0f)
                lineTo(19.42f, 5.99f)
                close()
                moveTo(7.05f, 18.36f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.03f, 0.0f, -1.41f)
                curveToRelative(-0.39f, -0.39f, -1.03f, -0.39f, -1.41f, 0.0f)
                lineToRelative(-1.06f, 1.06f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.03f, 0.0f, 1.41f)
                reflectiveCurveToRelative(1.03f, 0.39f, 1.41f, 0.0f)
                lineTo(7.05f, 18.36f)
                close()
            }
        }.also { _lightMode = it }
    }

private var _link: ImageVector? = null
val Icons.Rounded.Link: ImageVector
    get() {
        _link?.let { return it }
        return materialIcon(name = "Rounded.Link") {
            materialPath {
                moveTo(17.0f, 7.0f)
                horizontalLineToRelative(-3.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                reflectiveCurveToRelative(0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(3.0f)
                curveToRelative(1.65f, 0.0f, 3.0f, 1.35f, 3.0f, 3.0f)
                reflectiveCurveToRelative(-1.35f, 3.0f, -3.0f, 3.0f)
                horizontalLineToRelative(-3.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(3.0f)
                curveToRelative(2.76f, 0.0f, 5.0f, -2.24f, 5.0f, -5.0f)
                reflectiveCurveTo(19.76f, 7.0f, 17.0f, 7.0f)
                close()
                moveTo(8.0f, 12.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(6.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                reflectiveCurveToRelative(-0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineTo(9.0f)
                curveTo(8.45f, 11.0f, 8.0f, 11.45f, 8.0f, 12.0f)
                close()
                moveTo(10.0f, 15.0f)
                horizontalLineTo(7.0f)
                curveToRelative(-1.65f, 0.0f, -3.0f, -1.35f, -3.0f, -3.0f)
                reflectiveCurveToRelative(1.35f, -3.0f, 3.0f, -3.0f)
                horizontalLineToRelative(3.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                reflectiveCurveToRelative(-0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineTo(7.0f)
                curveToRelative(-2.76f, 0.0f, -5.0f, 2.24f, -5.0f, 5.0f)
                reflectiveCurveToRelative(2.24f, 5.0f, 5.0f, 5.0f)
                horizontalLineToRelative(3.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                curveTo(11.0f, 15.45f, 10.55f, 15.0f, 10.0f, 15.0f)
                close()
            }
        }.also { _link = it }
    }

private var _lockOpen: ImageVector? = null
val Icons.Rounded.LockOpen: ImageVector
    get() {
        _lockOpen?.let { return it }
        return materialIcon(name = "Rounded.LockOpen") {
            materialPath {
                moveTo(12.0f, 13.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                reflectiveCurveToRelative(0.9f, 2.0f, 2.0f, 2.0f)
                reflectiveCurveToRelative(2.0f, -0.9f, 2.0f, -2.0f)
                reflectiveCurveToRelative(-0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(18.0f, 8.0f)
                horizontalLineToRelative(-1.0f)
                lineTo(17.0f, 6.0f)
                curveToRelative(0.0f, -2.76f, -2.24f, -5.0f, -5.0f, -5.0f)
                curveToRelative(-2.28f, 0.0f, -4.27f, 1.54f, -4.84f, 3.75f)
                curveToRelative(-0.14f, 0.54f, 0.18f, 1.08f, 0.72f, 1.22f)
                curveToRelative(0.53f, 0.14f, 1.08f, -0.18f, 1.22f, -0.72f)
                curveTo(9.44f, 3.93f, 10.63f, 3.0f, 12.0f, 3.0f)
                curveToRelative(1.65f, 0.0f, 3.0f, 1.35f, 3.0f, 3.0f)
                verticalLineToRelative(2.0f)
                lineTo(6.0f, 8.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(10.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(12.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                lineTo(20.0f, 10.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(18.0f, 19.0f)
                curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
                lineTo(7.0f, 20.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                verticalLineToRelative(-8.0f)
                curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
                horizontalLineToRelative(10.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
                verticalLineToRelative(8.0f)
                close()
            }
        }.also { _lockOpen = it }
    }

private var _mail: ImageVector? = null
val Icons.Rounded.Mail: ImageVector
    get() {
        _mail?.let { return it }
        return materialIcon(name = "Rounded.Mail") {
            materialPath {
                moveTo(20.0f, 4.0f)
                lineTo(4.0f, 4.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(12.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(16.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                lineTo(22.0f, 6.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(19.6f, 8.25f)
                lineToRelative(-6.54f, 4.09f)
                curveToRelative(-0.65f, 0.41f, -1.47f, 0.41f, -2.12f, 0.0f)
                lineTo(4.4f, 8.25f)
                curveToRelative(-0.25f, -0.16f, -0.4f, -0.43f, -0.4f, -0.72f)
                curveToRelative(0.0f, -0.67f, 0.73f, -1.07f, 1.3f, -0.72f)
                lineTo(12.0f, 11.0f)
                lineToRelative(6.7f, -4.19f)
                curveToRelative(0.57f, -0.35f, 1.3f, 0.05f, 1.3f, 0.72f)
                curveToRelative(0.0f, 0.29f, -0.15f, 0.56f, -0.4f, 0.72f)
                close()
            }
        }.also { _mail = it }
    }

private var _movie: ImageVector? = null
val Icons.Rounded.Movie: ImageVector
    get() {
        _movie?.let { return it }
        return materialIcon(name = "Rounded.Movie") {
            materialPath {
                moveTo(18.0f, 4.0f)
                lineToRelative(1.82f, 3.64f)
                curveToRelative(0.08f, 0.16f, -0.04f, 0.36f, -0.22f, 0.36f)
                horizontalLineToRelative(-1.98f)
                curveToRelative(-0.38f, 0.0f, -0.73f, -0.21f, -0.89f, -0.55f)
                lineTo(15.0f, 4.0f)
                horizontalLineToRelative(-2.0f)
                lineToRelative(1.82f, 3.64f)
                curveToRelative(0.08f, 0.16f, -0.04f, 0.36f, -0.22f, 0.36f)
                horizontalLineToRelative(-1.98f)
                curveToRelative(-0.38f, 0.0f, -0.73f, -0.21f, -0.89f, -0.55f)
                lineTo(10.0f, 4.0f)
                horizontalLineTo(8.0f)
                lineToRelative(1.82f, 3.64f)
                curveToRelative(0.08f, 0.16f, -0.04f, 0.36f, -0.22f, 0.36f)
                horizontalLineTo(7.62f)
                curveToRelative(-0.38f, 0.0f, -0.73f, -0.21f, -0.9f, -0.55f)
                lineTo(5.0f, 4.0f)
                horizontalLineTo(4.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(12.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(16.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(5.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineToRelative(-3.0f)
                close()
            }
        }.also { _movie = it }
    }

private var _musicNote: ImageVector? = null
val Icons.Rounded.MusicNote: ImageVector
    get() {
        _musicNote?.let { return it }
        return materialIcon(name = "Rounded.MusicNote") {
            materialPath {
                moveTo(12.0f, 5.0f)
                verticalLineToRelative(8.55f)
                curveToRelative(-0.94f, -0.54f, -2.1f, -0.75f, -3.33f, -0.32f)
                curveToRelative(-1.34f, 0.48f, -2.37f, 1.67f, -2.61f, 3.07f)
                curveToRelative(-0.46f, 2.74f, 1.86f, 5.08f, 4.59f, 4.65f)
                curveToRelative(1.96f, -0.31f, 3.35f, -2.11f, 3.35f, -4.1f)
                verticalLineTo(7.0f)
                horizontalLineToRelative(2.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                reflectiveCurveToRelative(-0.9f, -2.0f, -2.0f, -2.0f)
                horizontalLineToRelative(-2.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                close()
            }
        }.also { _musicNote = it }
    }

private var _newspaper: ImageVector? = null
val Icons.Rounded.Newspaper: ImageVector
    get() {
        _newspaper?.let { return it }
        return materialIcon(name = "Rounded.Newspaper") {
            materialPath {
                moveTo(21.15f, 3.85f)
                lineToRelative(-0.82f, 0.82f)
                lineToRelative(-0.95f, -0.96f)
                curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.42f, 0.0f)
                lineTo(17.0f, 4.67f)
                lineToRelative(-0.96f, -0.96f)
                curveToRelative(-0.39f, -0.39f, -1.03f, -0.39f, -1.42f, 0.0f)
                lineToRelative(-0.95f, 0.96f)
                lineToRelative(-0.96f, -0.96f)
                curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0.0f)
                lineToRelative(-0.96f, 0.96f)
                lineTo(9.38f, 3.71f)
                curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.42f, 0.0f)
                lineTo(7.0f, 4.67f)
                lineTo(6.04f, 3.71f)
                curveToRelative(-0.39f, -0.39f, -1.03f, -0.39f, -1.42f, 0.0f)
                lineTo(3.67f, 4.67f)
                lineTo(2.85f, 3.85f)
                curveTo(2.54f, 3.54f, 2.0f, 3.76f, 2.0f, 4.21f)
                verticalLineTo(19.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                lineToRelative(16.0f, 0.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(4.21f)
                curveTo(22.0f, 3.76f, 21.46f, 3.54f, 21.15f, 3.85f)
                close()
                moveTo(11.0f, 19.0f)
                horizontalLineTo(4.0f)
                verticalLineToRelative(-6.0f)
                horizontalLineToRelative(7.0f)
                verticalLineTo(19.0f)
                close()
                moveTo(20.0f, 19.0f)
                horizontalLineToRelative(-7.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(7.0f)
                verticalLineTo(19.0f)
                close()
                moveTo(20.0f, 15.0f)
                horizontalLineToRelative(-7.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(7.0f)
                verticalLineTo(15.0f)
                close()
                moveTo(20.0f, 11.0f)
                horizontalLineTo(4.0f)
                verticalLineTo(8.0f)
                horizontalLineToRelative(16.0f)
                verticalLineTo(11.0f)
                close()
            }
        }.also { _newspaper = it }
    }

private var _openInBrowser: ImageVector? = null
val Icons.Rounded.OpenInBrowser: ImageVector
    get() {
        _openInBrowser?.let { return it }
        return materialIcon(name = "Rounded.OpenInBrowser") {
            materialPath {
                moveTo(19.0f, 4.0f)
                lineTo(5.0f, 4.0f)
                curveToRelative(-1.11f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(12.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(3.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                reflectiveCurveToRelative(-0.45f, -1.0f, -1.0f, -1.0f)
                lineTo(5.0f, 18.0f)
                lineTo(5.0f, 8.0f)
                horizontalLineToRelative(14.0f)
                verticalLineToRelative(10.0f)
                horizontalLineToRelative(-3.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                reflectiveCurveToRelative(0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(3.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                lineTo(21.0f, 6.0f)
                curveToRelative(0.0f, -1.1f, -0.89f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(11.65f, 10.35f)
                lineToRelative(-2.79f, 2.79f)
                curveToRelative(-0.32f, 0.32f, -0.1f, 0.86f, 0.35f, 0.86f)
                lineTo(11.0f, 14.0f)
                verticalLineToRelative(5.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                reflectiveCurveToRelative(1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineToRelative(-5.0f)
                horizontalLineToRelative(1.79f)
                curveToRelative(0.45f, 0.0f, 0.67f, -0.54f, 0.35f, -0.85f)
                lineToRelative(-2.79f, -2.79f)
                curveToRelative(-0.19f, -0.2f, -0.51f, -0.2f, -0.7f, -0.01f)
                close()
            }
        }.also { _openInBrowser = it }
    }

private var _palette: ImageVector? = null
val Icons.Rounded.Palette: ImageVector
    get() {
        _palette?.let { return it }
        return materialIcon(name = "Rounded.Palette") {
            materialPath {
                moveTo(12.0f, 2.0f)
                curveTo(6.49f, 2.0f, 2.0f, 6.49f, 2.0f, 12.0f)
                reflectiveCurveToRelative(4.49f, 10.0f, 10.0f, 10.0f)
                curveToRelative(1.38f, 0.0f, 2.5f, -1.12f, 2.5f, -2.5f)
                curveToRelative(0.0f, -0.61f, -0.23f, -1.2f, -0.64f, -1.67f)
                curveToRelative(-0.08f, -0.1f, -0.13f, -0.21f, -0.13f, -0.33f)
                curveToRelative(0.0f, -0.28f, 0.22f, -0.5f, 0.5f, -0.5f)
                horizontalLineTo(16.0f)
                curveToRelative(3.31f, 0.0f, 6.0f, -2.69f, 6.0f, -6.0f)
                curveTo(22.0f, 6.04f, 17.51f, 2.0f, 12.0f, 2.0f)
                close()
                moveTo(17.5f, 13.0f)
                curveToRelative(-0.83f, 0.0f, -1.5f, -0.67f, -1.5f, -1.5f)
                curveToRelative(0.0f, -0.83f, 0.67f, -1.5f, 1.5f, -1.5f)
                reflectiveCurveToRelative(1.5f, 0.67f, 1.5f, 1.5f)
                curveTo(19.0f, 12.33f, 18.33f, 13.0f, 17.5f, 13.0f)
                close()
                moveTo(14.5f, 9.0f)
                curveTo(13.67f, 9.0f, 13.0f, 8.33f, 13.0f, 7.5f)
                curveTo(13.0f, 6.67f, 13.67f, 6.0f, 14.5f, 6.0f)
                reflectiveCurveTo(16.0f, 6.67f, 16.0f, 7.5f)
                curveTo(16.0f, 8.33f, 15.33f, 9.0f, 14.5f, 9.0f)
                close()
                moveTo(5.0f, 11.5f)
                curveTo(5.0f, 10.67f, 5.67f, 10.0f, 6.5f, 10.0f)
                reflectiveCurveTo(8.0f, 10.67f, 8.0f, 11.5f)
                curveTo(8.0f, 12.33f, 7.33f, 13.0f, 6.5f, 13.0f)
                reflectiveCurveTo(5.0f, 12.33f, 5.0f, 11.5f)
                close()
                moveTo(11.0f, 7.5f)
                curveTo(11.0f, 8.33f, 10.33f, 9.0f, 9.5f, 9.0f)
                reflectiveCurveTo(8.0f, 8.33f, 8.0f, 7.5f)
                curveTo(8.0f, 6.67f, 8.67f, 6.0f, 9.5f, 6.0f)
                reflectiveCurveTo(11.0f, 6.67f, 11.0f, 7.5f)
                close()
            }
        }.also { _palette = it }
    }

private var _phoneAndroid: ImageVector? = null
val Icons.Rounded.PhoneAndroid: ImageVector
    get() {
        _phoneAndroid?.let { return it }
        return materialIcon(name = "Rounded.PhoneAndroid") {
            materialPath {
                moveTo(16.0f, 1.0f)
                lineTo(8.0f, 1.0f)
                curveTo(6.34f, 1.0f, 5.0f, 2.34f, 5.0f, 4.0f)
                verticalLineToRelative(16.0f)
                curveToRelative(0.0f, 1.66f, 1.34f, 3.0f, 3.0f, 3.0f)
                horizontalLineToRelative(8.0f)
                curveToRelative(1.66f, 0.0f, 3.0f, -1.34f, 3.0f, -3.0f)
                lineTo(19.0f, 4.0f)
                curveToRelative(0.0f, -1.66f, -1.34f, -3.0f, -3.0f, -3.0f)
                close()
                moveTo(13.5f, 21.0f)
                horizontalLineToRelative(-3.0f)
                curveToRelative(-0.28f, 0.0f, -0.5f, -0.22f, -0.5f, -0.5f)
                reflectiveCurveToRelative(0.22f, -0.5f, 0.5f, -0.5f)
                horizontalLineToRelative(3.0f)
                curveToRelative(0.28f, 0.0f, 0.5f, 0.22f, 0.5f, 0.5f)
                reflectiveCurveToRelative(-0.22f, 0.5f, -0.5f, 0.5f)
                close()
                moveTo(17.0f, 18.0f)
                lineTo(7.0f, 18.0f)
                lineTo(7.0f, 4.0f)
                horizontalLineToRelative(10.0f)
                verticalLineToRelative(14.0f)
                close()
            }
        }.also { _phoneAndroid = it }
    }

private var _photoLibrary: ImageVector? = null
val Icons.Rounded.PhotoLibrary: ImageVector
    get() {
        _photoLibrary?.let { return it }
        return materialIcon(name = "Rounded.PhotoLibrary") {
            materialPath {
                moveTo(22.0f, 16.0f)
                lineTo(22.0f, 4.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                lineTo(8.0f, 2.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(12.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(12.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                close()
                moveTo(11.4f, 12.53f)
                lineToRelative(1.63f, 2.18f)
                lineToRelative(2.58f, -3.22f)
                curveToRelative(0.2f, -0.25f, 0.58f, -0.25f, 0.78f, 0.0f)
                lineToRelative(2.96f, 3.7f)
                curveToRelative(0.26f, 0.33f, 0.03f, 0.81f, -0.39f, 0.81f)
                lineTo(9.0f, 16.0f)
                curveToRelative(-0.41f, 0.0f, -0.65f, -0.47f, -0.4f, -0.8f)
                lineToRelative(2.0f, -2.67f)
                curveToRelative(0.2f, -0.26f, 0.6f, -0.26f, 0.8f, 0.0f)
                close()
                moveTo(2.0f, 7.0f)
                verticalLineToRelative(13.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(13.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                reflectiveCurveToRelative(-0.45f, -1.0f, -1.0f, -1.0f)
                lineTo(5.0f, 20.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                lineTo(4.0f, 7.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                reflectiveCurveToRelative(-1.0f, 0.45f, -1.0f, 1.0f)
                close()
            }
        }.also { _photoLibrary = it }
    }

private var _pictureInPictureAlt: ImageVector? = null
val Icons.Rounded.PictureInPictureAlt: ImageVector
    get() {
        _pictureInPictureAlt?.let { return it }
        return materialIcon(name = "Rounded.PictureInPictureAlt") {
            materialPath {
                moveTo(18.0f, 11.0f)
                horizontalLineToRelative(-6.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                verticalLineToRelative(4.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(6.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineToRelative(-4.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                close()
                moveTo(23.0f, 19.0f)
                lineTo(23.0f, 4.98f)
                curveTo(23.0f, 3.88f, 22.1f, 3.0f, 21.0f, 3.0f)
                lineTo(3.0f, 3.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.88f, -2.0f, 1.98f)
                lineTo(1.0f, 19.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(18.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                close()
                moveTo(20.0f, 19.02f)
                lineTo(4.0f, 19.02f)
                curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                lineTo(3.0f, 5.97f)
                curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
                horizontalLineToRelative(16.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
                verticalLineToRelative(12.05f)
                curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
                close()
            }
        }.also { _pictureInPictureAlt = it }
    }

private var _print: ImageVector? = null
val Icons.Rounded.Print: ImageVector
    get() {
        _print?.let { return it }
        return materialIcon(name = "Rounded.Print") {
            materialPath {
                moveTo(19.0f, 8.0f)
                lineTo(5.0f, 8.0f)
                curveToRelative(-1.66f, 0.0f, -3.0f, 1.34f, -3.0f, 3.0f)
                verticalLineToRelative(4.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(2.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(8.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(2.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineToRelative(-4.0f)
                curveToRelative(0.0f, -1.66f, -1.34f, -3.0f, -3.0f, -3.0f)
                close()
                moveTo(15.0f, 19.0f)
                lineTo(9.0f, 19.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                verticalLineToRelative(-4.0f)
                horizontalLineToRelative(8.0f)
                verticalLineToRelative(4.0f)
                curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
                close()
                moveTo(19.0f, 12.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                reflectiveCurveToRelative(0.45f, -1.0f, 1.0f, -1.0f)
                reflectiveCurveToRelative(1.0f, 0.45f, 1.0f, 1.0f)
                reflectiveCurveToRelative(-0.45f, 1.0f, -1.0f, 1.0f)
                close()
                moveTo(17.0f, 3.0f)
                lineTo(7.0f, 3.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                verticalLineToRelative(2.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(10.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                lineTo(18.0f, 4.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                close()
            }
        }.also { _print = it }
    }

private var _public: ImageVector? = null
val Icons.Rounded.Public: ImageVector
    get() {
        _public?.let { return it }
        return materialIcon(name = "Rounded.Public") {
            materialPath {
                moveTo(12.0f, 2.0f)
                curveTo(6.48f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                reflectiveCurveToRelative(4.48f, 10.0f, 10.0f, 10.0f)
                reflectiveCurveToRelative(10.0f, -4.48f, 10.0f, -10.0f)
                reflectiveCurveTo(17.52f, 2.0f, 12.0f, 2.0f)
                close()
                moveTo(11.0f, 19.93f)
                curveToRelative(-3.95f, -0.49f, -7.0f, -3.85f, -7.0f, -7.93f)
                curveToRelative(0.0f, -0.62f, 0.08f, -1.21f, 0.21f, -1.79f)
                lineTo(9.0f, 15.0f)
                verticalLineToRelative(1.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                verticalLineToRelative(1.93f)
                close()
                moveTo(17.9f, 17.39f)
                curveToRelative(-0.26f, -0.81f, -1.0f, -1.39f, -1.9f, -1.39f)
                horizontalLineToRelative(-1.0f)
                verticalLineToRelative(-3.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                lineTo(8.0f, 12.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(2.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                lineTo(11.0f, 7.0f)
                horizontalLineToRelative(2.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineToRelative(-0.41f)
                curveToRelative(2.93f, 1.19f, 5.0f, 4.06f, 5.0f, 7.41f)
                curveToRelative(0.0f, 2.08f, -0.8f, 3.97f, -2.1f, 5.39f)
                close()
            }
        }.also { _public = it }
    }

private var _pushPin: ImageVector? = null
val Icons.Rounded.PushPin: ImageVector
    get() {
        _pushPin?.let { return it }
        return materialIcon(name = "Rounded.PushPin") {
            materialPath(pathFillType = PathFillType.EvenOdd) {
                moveTo(19.0f, 12.87f)
                curveToRelative(0.0f, -0.47f, -0.34f, -0.85f, -0.8f, -0.98f)
                curveTo(16.93f, 11.54f, 16.0f, 10.38f, 16.0f, 9.0f)
                verticalLineTo(4.0f)
                lineToRelative(1.0f, 0.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineTo(7.0f)
                curveTo(6.45f, 2.0f, 6.0f, 2.45f, 6.0f, 3.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                lineToRelative(1.0f, 0.0f)
                verticalLineToRelative(5.0f)
                curveToRelative(0.0f, 1.38f, -0.93f, 2.54f, -2.2f, 2.89f)
                curveTo(5.34f, 12.02f, 5.0f, 12.4f, 5.0f, 12.87f)
                verticalLineTo(13.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(4.98f)
                lineTo(11.0f, 21.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                lineToRelative(-0.02f, -7.0f)
                horizontalLineTo(18.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineTo(12.87f)
                close()
            }
        }.also { _pushPin = it }
    }

private var _qrCodeScanner: ImageVector? = null
val Icons.Rounded.QrCodeScanner: ImageVector
    get() {
        _qrCodeScanner?.let { return it }
        return materialIcon(name = "Rounded.QrCodeScanner") {
            materialPath {
                moveTo(9.5f, 6.5f)
                verticalLineToRelative(3.0f)
                horizontalLineToRelative(-3.0f)
                verticalLineToRelative(-3.0f)
                horizontalLineTo(9.5f)
                moveTo(11.0f, 5.0f)
                horizontalLineTo(5.0f)
                verticalLineToRelative(6.0f)
                horizontalLineToRelative(6.0f)
                verticalLineTo(5.0f)
                lineTo(11.0f, 5.0f)
                close()
                moveTo(9.5f, 14.5f)
                verticalLineToRelative(3.0f)
                horizontalLineToRelative(-3.0f)
                verticalLineToRelative(-3.0f)
                horizontalLineTo(9.5f)
                moveTo(11.0f, 13.0f)
                horizontalLineTo(5.0f)
                verticalLineToRelative(6.0f)
                horizontalLineToRelative(6.0f)
                verticalLineTo(13.0f)
                lineTo(11.0f, 13.0f)
                close()
                moveTo(17.5f, 6.5f)
                verticalLineToRelative(3.0f)
                horizontalLineToRelative(-3.0f)
                verticalLineToRelative(-3.0f)
                horizontalLineTo(17.5f)
                moveTo(19.0f, 5.0f)
                horizontalLineToRelative(-6.0f)
                verticalLineToRelative(6.0f)
                horizontalLineToRelative(6.0f)
                verticalLineTo(5.0f)
                lineTo(19.0f, 5.0f)
                close()
                moveTo(13.0f, 13.0f)
                horizontalLineToRelative(1.5f)
                verticalLineToRelative(1.5f)
                horizontalLineTo(13.0f)
                verticalLineTo(13.0f)
                close()
                moveTo(14.5f, 14.5f)
                horizontalLineTo(16.0f)
                verticalLineTo(16.0f)
                horizontalLineToRelative(-1.5f)
                verticalLineTo(14.5f)
                close()
                moveTo(16.0f, 13.0f)
                horizontalLineToRelative(1.5f)
                verticalLineToRelative(1.5f)
                horizontalLineTo(16.0f)
                verticalLineTo(13.0f)
                close()
                moveTo(13.0f, 16.0f)
                horizontalLineToRelative(1.5f)
                verticalLineToRelative(1.5f)
                horizontalLineTo(13.0f)
                verticalLineTo(16.0f)
                close()
                moveTo(14.5f, 17.5f)
                horizontalLineTo(16.0f)
                verticalLineTo(19.0f)
                horizontalLineToRelative(-1.5f)
                verticalLineTo(17.5f)
                close()
                moveTo(16.0f, 16.0f)
                horizontalLineToRelative(1.5f)
                verticalLineToRelative(1.5f)
                horizontalLineTo(16.0f)
                verticalLineTo(16.0f)
                close()
                moveTo(17.5f, 14.5f)
                horizontalLineTo(19.0f)
                verticalLineTo(16.0f)
                horizontalLineToRelative(-1.5f)
                verticalLineTo(14.5f)
                close()
                moveTo(17.5f, 17.5f)
                horizontalLineTo(19.0f)
                verticalLineTo(19.0f)
                horizontalLineToRelative(-1.5f)
                verticalLineTo(17.5f)
                close()
                moveTo(21.0f, 7.0f)
                lineTo(21.0f, 7.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                verticalLineTo(4.0f)
                horizontalLineToRelative(-2.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                verticalLineToRelative(0.0f)
                curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
                horizontalLineToRelative(3.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
                verticalLineToRelative(3.0f)
                curveTo(22.0f, 6.55f, 21.55f, 7.0f, 21.0f, 7.0f)
                close()
                moveTo(22.0f, 21.0f)
                verticalLineToRelative(-3.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineToRelative(0.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(-2.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                verticalLineToRelative(0.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(3.0f)
                curveTo(21.55f, 22.0f, 22.0f, 21.55f, 22.0f, 21.0f)
                close()
                moveTo(3.0f, 22.0f)
                horizontalLineToRelative(3.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineToRelative(0.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineTo(4.0f)
                verticalLineToRelative(-2.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineToRelative(0.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                verticalLineToRelative(3.0f)
                curveTo(2.0f, 21.55f, 2.45f, 22.0f, 3.0f, 22.0f)
                close()
                moveTo(2.0f, 3.0f)
                verticalLineToRelative(3.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(0.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineTo(4.0f)
                horizontalLineToRelative(2.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineToRelative(0.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineTo(3.0f)
                curveTo(2.45f, 2.0f, 2.0f, 2.45f, 2.0f, 3.0f)
                close()
            }
        }.also { _qrCodeScanner = it }
    }

private var _remove: ImageVector? = null
val Icons.Rounded.Remove: ImageVector
    get() {
        _remove?.let { return it }
        return materialIcon(name = "Rounded.Remove") {
            materialPath {
                moveTo(18.0f, 13.0f)
                horizontalLineTo(6.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                reflectiveCurveToRelative(0.45f, -1.0f, 1.0f, -1.0f)
                horizontalLineToRelative(12.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
                reflectiveCurveToRelative(-0.45f, 1.0f, -1.0f, 1.0f)
                close()
            }
        }.also { _remove = it }
    }

private var _restartAlt: ImageVector? = null
val Icons.Rounded.RestartAlt: ImageVector
    get() {
        _restartAlt?.let { return it }
        return materialIcon(name = "Rounded.RestartAlt") {
            materialPath {
                moveTo(12.0f, 5.0f)
                verticalLineTo(3.21f)
                curveToRelative(0.0f, -0.45f, -0.54f, -0.67f, -0.85f, -0.35f)
                lineTo(8.35f, 5.65f)
                curveToRelative(-0.2f, 0.2f, -0.2f, 0.51f, 0.0f, 0.71f)
                lineToRelative(2.79f, 2.79f)
                curveTo(11.46f, 9.46f, 12.0f, 9.24f, 12.0f, 8.79f)
                verticalLineTo(7.0f)
                curveToRelative(3.31f, 0.0f, 6.0f, 2.69f, 6.0f, 6.0f)
                curveToRelative(0.0f, 2.72f, -1.83f, 5.02f, -4.31f, 5.75f)
                curveTo(13.27f, 18.87f, 13.0f, 19.27f, 13.0f, 19.7f)
                verticalLineToRelative(0.0f)
                curveToRelative(0.0f, 0.65f, 0.62f, 1.16f, 1.25f, 0.97f)
                curveTo(17.57f, 19.7f, 20.0f, 16.64f, 20.0f, 13.0f)
                curveTo(20.0f, 8.58f, 16.42f, 5.0f, 12.0f, 5.0f)
                close()
            }
            materialPath {
                moveTo(6.0f, 13.0f)
                curveToRelative(0.0f, -1.34f, 0.44f, -2.58f, 1.19f, -3.59f)
                curveToRelative(0.3f, -0.4f, 0.26f, -0.95f, -0.09f, -1.31f)
                lineToRelative(0.0f, 0.0f)
                curveTo(6.68f, 7.68f, 5.96f, 7.72f, 5.6f, 8.2f)
                curveTo(4.6f, 9.54f, 4.0f, 11.2f, 4.0f, 13.0f)
                curveToRelative(0.0f, 3.64f, 2.43f, 6.7f, 5.75f, 7.67f)
                curveTo(10.38f, 20.86f, 11.0f, 20.35f, 11.0f, 19.7f)
                verticalLineToRelative(0.0f)
                curveToRelative(0.0f, -0.43f, -0.27f, -0.83f, -0.69f, -0.95f)
                curveTo(7.83f, 18.02f, 6.0f, 15.72f, 6.0f, 13.0f)
                close()
            }
        }.also { _restartAlt = it }
    }

private var _restaurant: ImageVector? = null
val Icons.Rounded.Restaurant: ImageVector
    get() {
        _restaurant?.let { return it }
        return materialIcon(name = "Rounded.Restaurant") {
            materialPath {
                moveTo(16.0f, 6.0f)
                verticalLineToRelative(6.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(1.0f)
                verticalLineToRelative(7.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                reflectiveCurveToRelative(1.0f, -0.45f, 1.0f, -1.0f)
                lineTo(21.0f, 3.13f)
                curveToRelative(0.0f, -0.65f, -0.61f, -1.13f, -1.24f, -0.98f)
                curveTo(17.6f, 2.68f, 16.0f, 4.51f, 16.0f, 6.0f)
                close()
                moveTo(11.0f, 9.0f)
                lineTo(9.0f, 9.0f)
                lineTo(9.0f, 3.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                reflectiveCurveToRelative(-1.0f, 0.45f, -1.0f, 1.0f)
                verticalLineToRelative(6.0f)
                lineTo(5.0f, 9.0f)
                lineTo(5.0f, 3.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                reflectiveCurveToRelative(-1.0f, 0.45f, -1.0f, 1.0f)
                verticalLineToRelative(6.0f)
                curveToRelative(0.0f, 2.21f, 1.79f, 4.0f, 4.0f, 4.0f)
                verticalLineToRelative(8.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                reflectiveCurveToRelative(1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineToRelative(-8.0f)
                curveToRelative(2.21f, 0.0f, 4.0f, -1.79f, 4.0f, -4.0f)
                lineTo(13.0f, 3.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                reflectiveCurveToRelative(-1.0f, 0.45f, -1.0f, 1.0f)
                verticalLineToRelative(6.0f)
                close()
            }
        }.also { _restaurant = it }
    }

private var _rocketLaunch: ImageVector? = null
val Icons.Rounded.RocketLaunch: ImageVector
    get() {
        _rocketLaunch?.let { return it }
        return materialIcon(name = "Rounded.RocketLaunch") {
            materialPath {
                moveTo(9.19f, 6.35f)
                curveToRelative(-2.04f, 2.29f, -3.44f, 5.58f, -3.57f, 5.89f)
                lineToRelative(-2.26f, -0.97f)
                curveToRelative(-0.65f, -0.28f, -0.81f, -1.13f, -0.31f, -1.63f)
                lineToRelative(3.01f, -3.01f)
                curveToRelative(0.47f, -0.47f, 1.15f, -0.68f, 1.81f, -0.55f)
                lineTo(9.19f, 6.35f)
                lineTo(9.19f, 6.35f)
                close()
                moveTo(10.68f, 16.51f)
                curveToRelative(0.3f, 0.3f, 0.74f, 0.38f, 1.12f, 0.2f)
                curveToRelative(1.16f, -0.54f, 3.65f, -1.81f, 5.26f, -3.42f)
                curveToRelative(4.59f, -4.59f, 4.63f, -8.33f, 4.36f, -9.93f)
                curveToRelative(-0.07f, -0.4f, -0.39f, -0.72f, -0.79f, -0.79f)
                curveToRelative(-1.6f, -0.27f, -5.34f, -0.23f, -9.93f, 4.36f)
                curveToRelative(-1.61f, 1.61f, -2.87f, 4.1f, -3.42f, 5.26f)
                curveToRelative(-0.18f, 0.38f, -0.09f, 0.83f, 0.2f, 1.12f)
                lineTo(10.68f, 16.51f)
                close()
                moveTo(17.65f, 14.81f)
                curveToRelative(-2.29f, 2.04f, -5.58f, 3.44f, -5.89f, 3.57f)
                lineToRelative(0.97f, 2.26f)
                curveToRelative(0.28f, 0.65f, 1.13f, 0.81f, 1.63f, 0.31f)
                lineToRelative(3.01f, -3.01f)
                curveToRelative(0.47f, -0.47f, 0.68f, -1.15f, 0.55f, -1.81f)
                lineTo(17.65f, 14.81f)
                lineTo(17.65f, 14.81f)
                close()
                moveTo(8.94f, 17.41f)
                curveToRelative(0.2f, 1.06f, -0.15f, 2.04f, -0.82f, 2.71f)
                curveToRelative(-0.77f, 0.77f, -3.16f, 1.34f, -4.71f, 1.64f)
                curveToRelative(-0.69f, 0.13f, -1.3f, -0.48f, -1.17f, -1.17f)
                curveToRelative(0.3f, -1.55f, 0.86f, -3.94f, 1.64f, -4.71f)
                curveToRelative(0.67f, -0.67f, 1.65f, -1.02f, 2.71f, -0.82f)
                curveTo(7.76f, 15.28f, 8.72f, 16.24f, 8.94f, 17.41f)
                close()
                moveTo(13.0f, 9.0f)
                curveToRelative(0.0f, -1.1f, 0.9f, -2.0f, 2.0f, -2.0f)
                reflectiveCurveToRelative(2.0f, 0.9f, 2.0f, 2.0f)
                reflectiveCurveToRelative(-0.9f, 2.0f, -2.0f, 2.0f)
                reflectiveCurveTo(13.0f, 10.1f, 13.0f, 9.0f)
                close()
            }
        }.also { _rocketLaunch = it }
    }

private var _school: ImageVector? = null
val Icons.Rounded.School: ImageVector
    get() {
        _school?.let { return it }
        return materialIcon(name = "Rounded.School") {
            materialPath {
                moveTo(5.0f, 13.18f)
                verticalLineToRelative(2.81f)
                curveToRelative(0.0f, 0.73f, 0.4f, 1.41f, 1.04f, 1.76f)
                lineToRelative(5.0f, 2.73f)
                curveToRelative(0.6f, 0.33f, 1.32f, 0.33f, 1.92f, 0.0f)
                lineToRelative(5.0f, -2.73f)
                curveToRelative(0.64f, -0.35f, 1.04f, -1.03f, 1.04f, -1.76f)
                verticalLineToRelative(-2.81f)
                lineToRelative(-6.04f, 3.3f)
                curveToRelative(-0.6f, 0.33f, -1.32f, 0.33f, -1.92f, 0.0f)
                lineTo(5.0f, 13.18f)
                close()
                moveTo(11.04f, 3.52f)
                lineToRelative(-8.43f, 4.6f)
                curveToRelative(-0.69f, 0.38f, -0.69f, 1.38f, 0.0f, 1.76f)
                lineToRelative(8.43f, 4.6f)
                curveToRelative(0.6f, 0.33f, 1.32f, 0.33f, 1.92f, 0.0f)
                lineTo(21.0f, 10.09f)
                lineTo(21.0f, 16.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                reflectiveCurveToRelative(1.0f, -0.45f, 1.0f, -1.0f)
                lineTo(23.0f, 9.59f)
                curveToRelative(0.0f, -0.37f, -0.2f, -0.7f, -0.52f, -0.88f)
                lineToRelative(-9.52f, -5.19f)
                curveToRelative(-0.6f, -0.32f, -1.32f, -0.32f, -1.92f, 0.0f)
                close()
            }
        }.also { _school = it }
    }

private var _science: ImageVector? = null
val Icons.Rounded.Science: ImageVector
    get() {
        _science?.let { return it }
        return materialIcon(name = "Rounded.Science") {
            materialPath {
                moveTo(20.54f, 17.73f)
                lineTo(15.0f, 11.0f)
                verticalLineTo(5.0f)
                horizontalLineToRelative(1.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                reflectiveCurveToRelative(-0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineTo(8.0f)
                curveTo(7.45f, 3.0f, 7.0f, 3.45f, 7.0f, 4.0f)
                reflectiveCurveToRelative(0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(1.0f)
                verticalLineToRelative(6.0f)
                lineToRelative(-5.54f, 6.73f)
                curveTo(3.14f, 18.12f, 3.0f, 18.56f, 3.0f, 19.0f)
                curveToRelative(0.01f, 1.03f, 0.82f, 2.0f, 2.0f, 2.0f)
                horizontalLineTo(19.0f)
                curveToRelative(1.19f, 0.0f, 2.0f, -0.97f, 2.0f, -2.0f)
                curveTo(21.0f, 18.56f, 20.86f, 18.12f, 20.54f, 17.73f)
                close()
            }
        }.also { _science = it }
    }

private var _security: ImageVector? = null
val Icons.Rounded.Security: ImageVector
    get() {
        _security?.let { return it }
        return materialIcon(name = "Rounded.Security") {
            materialPath {
                moveTo(11.19f, 1.36f)
                lineToRelative(-7.0f, 3.11f)
                curveTo(3.47f, 4.79f, 3.0f, 5.51f, 3.0f, 6.3f)
                verticalLineTo(11.0f)
                curveToRelative(0.0f, 5.55f, 3.84f, 10.74f, 9.0f, 12.0f)
                curveToRelative(5.16f, -1.26f, 9.0f, -6.45f, 9.0f, -12.0f)
                verticalLineTo(6.3f)
                curveToRelative(0.0f, -0.79f, -0.47f, -1.51f, -1.19f, -1.83f)
                lineToRelative(-7.0f, -3.11f)
                curveToRelative(-0.51f, -0.23f, -1.11f, -0.23f, -1.62f, 0.0f)
                close()
                moveTo(12.0f, 11.99f)
                horizontalLineToRelative(7.0f)
                curveToRelative(-0.53f, 4.12f, -3.28f, 7.79f, -7.0f, 8.94f)
                verticalLineTo(12.0f)
                horizontalLineTo(5.0f)
                verticalLineTo(6.3f)
                lineToRelative(7.0f, -3.11f)
                verticalLineToRelative(8.8f)
                close()
            }
        }.also { _security = it }
    }

private var _shield: ImageVector? = null
val Icons.Rounded.Shield: ImageVector
    get() {
        _shield?.let { return it }
        return materialIcon(name = "Rounded.Shield") {
            materialPath {
                moveTo(11.3f, 2.26f)
                lineToRelative(-6.0f, 2.25f)
                curveTo(4.52f, 4.81f, 4.0f, 5.55f, 4.0f, 6.39f)
                verticalLineToRelative(4.7f)
                curveToRelative(0.0f, 4.83f, 3.13f, 9.37f, 7.43f, 10.75f)
                curveToRelative(0.37f, 0.12f, 0.77f, 0.12f, 1.14f, 0.0f)
                curveToRelative(4.3f, -1.38f, 7.43f, -5.91f, 7.43f, -10.75f)
                verticalLineToRelative(-4.7f)
                curveToRelative(0.0f, -0.83f, -0.52f, -1.58f, -1.3f, -1.87f)
                lineToRelative(-6.0f, -2.25f)
                curveTo(12.25f, 2.09f, 11.75f, 2.09f, 11.3f, 2.26f)
                close()
            }
        }.also { _shield = it }
    }

private var _shoppingBag: ImageVector? = null
val Icons.Rounded.ShoppingBag: ImageVector
    get() {
        _shoppingBag?.let { return it }
        return materialIcon(name = "Rounded.ShoppingBag") {
            materialPath {
                moveTo(18.0f, 6.0f)
                horizontalLineToRelative(-2.0f)
                curveToRelative(0.0f, -2.21f, -1.79f, -4.0f, -4.0f, -4.0f)
                reflectiveCurveTo(8.0f, 3.79f, 8.0f, 6.0f)
                horizontalLineTo(6.0f)
                curveTo(4.9f, 6.0f, 4.0f, 6.9f, 4.0f, 8.0f)
                verticalLineToRelative(12.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(12.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(8.0f)
                curveTo(20.0f, 6.9f, 19.1f, 6.0f, 18.0f, 6.0f)
                close()
                moveTo(10.0f, 10.0f)
                curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
                reflectiveCurveToRelative(-1.0f, -0.45f, -1.0f, -1.0f)
                verticalLineTo(8.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(10.0f)
                close()
                moveTo(12.0f, 4.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, 0.9f, 2.0f, 2.0f)
                horizontalLineToRelative(-4.0f)
                curveTo(10.0f, 4.9f, 10.9f, 4.0f, 12.0f, 4.0f)
                close()
                moveTo(16.0f, 10.0f)
                curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
                reflectiveCurveToRelative(-1.0f, -0.45f, -1.0f, -1.0f)
                verticalLineTo(8.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(10.0f)
                close()
            }
        }.also { _shoppingBag = it }
    }

private var _smartphone: ImageVector? = null
val Icons.Rounded.Smartphone: ImageVector
    get() {
        _smartphone?.let { return it }
        return materialIcon(name = "Rounded.Smartphone") {
            materialPath {
                moveTo(17.0f, 1.01f)
                lineTo(7.0f, 1.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(18.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(10.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(3.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -1.99f, -2.0f, -1.99f)
                close()
                moveTo(17.0f, 19.0f)
                horizontalLineTo(7.0f)
                verticalLineTo(5.0f)
                horizontalLineToRelative(10.0f)
                verticalLineToRelative(14.0f)
                close()
            }
        }.also { _smartphone = it }
    }

private var _sportsEsports: ImageVector? = null
val Icons.Rounded.SportsEsports: ImageVector
    get() {
        _sportsEsports?.let { return it }
        return materialIcon(name = "Rounded.SportsEsports") {
            materialPath {
                moveTo(21.58f, 16.09f)
                lineToRelative(-1.09f, -7.66f)
                curveTo(20.21f, 6.46f, 18.52f, 5.0f, 16.53f, 5.0f)
                horizontalLineTo(7.47f)
                curveTo(5.48f, 5.0f, 3.79f, 6.46f, 3.51f, 8.43f)
                lineToRelative(-1.09f, 7.66f)
                curveTo(2.2f, 17.63f, 3.39f, 19.0f, 4.94f, 19.0f)
                horizontalLineToRelative(0.0f)
                curveToRelative(0.68f, 0.0f, 1.32f, -0.27f, 1.8f, -0.75f)
                lineTo(9.0f, 16.0f)
                horizontalLineToRelative(6.0f)
                lineToRelative(2.25f, 2.25f)
                curveToRelative(0.48f, 0.48f, 1.13f, 0.75f, 1.8f, 0.75f)
                horizontalLineToRelative(0.0f)
                curveTo(20.61f, 19.0f, 21.8f, 17.63f, 21.58f, 16.09f)
                close()
                moveTo(11.0f, 11.0f)
                horizontalLineTo(9.0f)
                verticalLineToRelative(2.0f)
                horizontalLineTo(8.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineTo(6.0f)
                verticalLineToRelative(-1.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(8.0f)
                horizontalLineToRelative(1.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(11.0f)
                close()
                moveTo(15.0f, 10.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
                reflectiveCurveToRelative(1.0f, 0.45f, 1.0f, 1.0f)
                curveTo(16.0f, 9.55f, 15.55f, 10.0f, 15.0f, 10.0f)
                close()
                moveTo(17.0f, 13.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
                reflectiveCurveToRelative(1.0f, 0.45f, 1.0f, 1.0f)
                curveTo(18.0f, 12.55f, 17.55f, 13.0f, 17.0f, 13.0f)
                close()
            }
        }.also { _sportsEsports = it }
    }

private var _starOutline: ImageVector? = null
val Icons.Rounded.StarOutline: ImageVector
    get() {
        _starOutline?.let { return it }
        return materialIcon(name = "Rounded.StarOutline") {
            materialPath {
                moveTo(19.65f, 9.04f)
                lineToRelative(-4.84f, -0.42f)
                lineToRelative(-1.89f, -4.45f)
                curveToRelative(-0.34f, -0.81f, -1.5f, -0.81f, -1.84f, 0.0f)
                lineTo(9.19f, 8.63f)
                lineToRelative(-4.83f, 0.41f)
                curveToRelative(-0.88f, 0.07f, -1.24f, 1.17f, -0.57f, 1.75f)
                lineToRelative(3.67f, 3.18f)
                lineToRelative(-1.1f, 4.72f)
                curveToRelative(-0.2f, 0.86f, 0.73f, 1.54f, 1.49f, 1.08f)
                lineToRelative(4.15f, -2.5f)
                lineToRelative(4.15f, 2.51f)
                curveToRelative(0.76f, 0.46f, 1.69f, -0.22f, 1.49f, -1.08f)
                lineToRelative(-1.1f, -4.73f)
                lineToRelative(3.67f, -3.18f)
                curveToRelative(0.67f, -0.58f, 0.32f, -1.68f, -0.56f, -1.75f)
                close()
                moveTo(12.0f, 15.4f)
                lineToRelative(-3.76f, 2.27f)
                lineToRelative(1.0f, -4.28f)
                lineToRelative(-3.32f, -2.88f)
                lineToRelative(4.38f, -0.38f)
                lineTo(12.0f, 6.1f)
                lineToRelative(1.71f, 4.04f)
                lineToRelative(4.38f, 0.38f)
                lineToRelative(-3.32f, 2.88f)
                lineToRelative(1.0f, 4.28f)
                lineTo(12.0f, 15.4f)
                close()
            }
        }.also { _starOutline = it }
    }

private var _swapVert: ImageVector? = null
val Icons.Rounded.SwapVert: ImageVector
    get() {
        _swapVert?.let { return it }
        return materialIcon(name = "Rounded.SwapVert") {
            materialPath {
                moveTo(16.0f, 17.01f)
                verticalLineTo(11.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                reflectiveCurveToRelative(-1.0f, 0.45f, -1.0f, 1.0f)
                verticalLineToRelative(6.01f)
                horizontalLineToRelative(-1.79f)
                curveToRelative(-0.45f, 0.0f, -0.67f, 0.54f, -0.35f, 0.85f)
                lineToRelative(2.79f, 2.78f)
                curveToRelative(0.2f, 0.19f, 0.51f, 0.19f, 0.71f, 0.0f)
                lineToRelative(2.79f, -2.78f)
                curveToRelative(0.32f, -0.31f, 0.09f, -0.85f, -0.35f, -0.85f)
                horizontalLineTo(16.0f)
                close()
                moveTo(8.65f, 3.35f)
                lineTo(5.86f, 6.14f)
                curveToRelative(-0.32f, 0.31f, -0.1f, 0.85f, 0.35f, 0.85f)
                horizontalLineTo(8.0f)
                verticalLineTo(13.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                reflectiveCurveToRelative(1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineTo(6.99f)
                horizontalLineToRelative(1.79f)
                curveToRelative(0.45f, 0.0f, 0.67f, -0.54f, 0.35f, -0.85f)
                lineTo(9.35f, 3.35f)
                curveToRelative(-0.19f, -0.19f, -0.51f, -0.19f, -0.7f, 0.0f)
                close()
            }
        }.also { _swapVert = it }
    }

private var _sync: ImageVector? = null
val Icons.Rounded.Sync: ImageVector
    get() {
        _sync?.let { return it }
        return materialIcon(name = "Rounded.Sync") {
            materialPath {
                moveTo(12.0f, 4.0f)
                lineTo(12.0f, 2.21f)
                curveToRelative(0.0f, -0.45f, -0.54f, -0.67f, -0.85f, -0.35f)
                lineToRelative(-2.8f, 2.79f)
                curveToRelative(-0.2f, 0.2f, -0.2f, 0.51f, 0.0f, 0.71f)
                lineToRelative(2.79f, 2.79f)
                curveToRelative(0.32f, 0.31f, 0.86f, 0.09f, 0.86f, -0.36f)
                lineTo(12.0f, 6.0f)
                curveToRelative(3.31f, 0.0f, 6.0f, 2.69f, 6.0f, 6.0f)
                curveToRelative(0.0f, 0.79f, -0.15f, 1.56f, -0.44f, 2.25f)
                curveToRelative(-0.15f, 0.36f, -0.04f, 0.77f, 0.23f, 1.04f)
                curveToRelative(0.51f, 0.51f, 1.37f, 0.33f, 1.64f, -0.34f)
                curveToRelative(0.37f, -0.91f, 0.57f, -1.91f, 0.57f, -2.95f)
                curveToRelative(0.0f, -4.42f, -3.58f, -8.0f, -8.0f, -8.0f)
                close()
                moveTo(12.0f, 18.0f)
                curveToRelative(-3.31f, 0.0f, -6.0f, -2.69f, -6.0f, -6.0f)
                curveToRelative(0.0f, -0.79f, 0.15f, -1.56f, 0.44f, -2.25f)
                curveToRelative(0.15f, -0.36f, 0.04f, -0.77f, -0.23f, -1.04f)
                curveToRelative(-0.51f, -0.51f, -1.37f, -0.33f, -1.64f, 0.34f)
                curveTo(4.2f, 9.96f, 4.0f, 10.96f, 4.0f, 12.0f)
                curveToRelative(0.0f, 4.42f, 3.58f, 8.0f, 8.0f, 8.0f)
                verticalLineToRelative(1.79f)
                curveToRelative(0.0f, 0.45f, 0.54f, 0.67f, 0.85f, 0.35f)
                lineToRelative(2.79f, -2.79f)
                curveToRelative(0.2f, -0.2f, 0.2f, -0.51f, 0.0f, -0.71f)
                lineToRelative(-2.79f, -2.79f)
                curveToRelative(-0.31f, -0.31f, -0.85f, -0.09f, -0.85f, 0.36f)
                lineTo(12.0f, 18.0f)
                close()
            }
        }.also { _sync = it }
    }

private var _tab: ImageVector? = null
val Icons.Rounded.Tab: ImageVector
    get() {
        _tab?.let { return it }
        return materialIcon(name = "Rounded.Tab") {
            materialPath {
                moveTo(21.0f, 3.0f)
                lineTo(3.0f, 3.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(14.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(18.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                lineTo(23.0f, 5.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(20.0f, 19.0f)
                lineTo(4.0f, 19.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                lineTo(3.0f, 6.0f)
                curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
                horizontalLineToRelative(9.0f)
                verticalLineToRelative(3.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(7.0f)
                verticalLineToRelative(9.0f)
                curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
                close()
            }
        }.also { _tab = it }
    }

private var _tabletAndroid: ImageVector? = null
val Icons.Rounded.TabletAndroid: ImageVector
    get() {
        _tabletAndroid?.let { return it }
        return materialIcon(name = "Rounded.TabletAndroid") {
            materialPath {
                moveTo(18.0f, 0.0f)
                lineTo(6.0f, 0.0f)
                curveTo(4.34f, 0.0f, 3.0f, 1.34f, 3.0f, 3.0f)
                verticalLineToRelative(18.0f)
                curveToRelative(0.0f, 1.66f, 1.34f, 3.0f, 3.0f, 3.0f)
                horizontalLineToRelative(12.0f)
                curveToRelative(1.66f, 0.0f, 3.0f, -1.34f, 3.0f, -3.0f)
                lineTo(21.0f, 3.0f)
                curveToRelative(0.0f, -1.66f, -1.34f, -3.0f, -3.0f, -3.0f)
                close()
                moveTo(13.5f, 22.0f)
                horizontalLineToRelative(-3.0f)
                curveToRelative(-0.28f, 0.0f, -0.5f, -0.22f, -0.5f, -0.5f)
                reflectiveCurveToRelative(0.22f, -0.5f, 0.5f, -0.5f)
                horizontalLineToRelative(3.0f)
                curveToRelative(0.28f, 0.0f, 0.5f, 0.22f, 0.5f, 0.5f)
                reflectiveCurveToRelative(-0.22f, 0.5f, -0.5f, 0.5f)
                close()
                moveTo(19.25f, 19.0f)
                lineTo(4.75f, 19.0f)
                lineTo(4.75f, 3.0f)
                horizontalLineToRelative(14.5f)
                verticalLineToRelative(16.0f)
                close()
            }
        }.also { _tabletAndroid = it }
    }

private var _terminal: ImageVector? = null
val Icons.Rounded.Terminal: ImageVector
    get() {
        _terminal?.let { return it }
        return materialIcon(name = "Rounded.Terminal") {
            materialPath {
                moveTo(20.0f, 4.0f)
                horizontalLineTo(4.0f)
                curveTo(2.89f, 4.0f, 2.0f, 4.9f, 2.0f, 6.0f)
                verticalLineToRelative(12.0f)
                curveToRelative(0.0f, 1.1f, 0.89f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(16.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(6.0f)
                curveTo(22.0f, 4.9f, 21.11f, 4.0f, 20.0f, 4.0f)
                close()
                moveTo(20.0f, 18.0f)
                horizontalLineTo(4.0f)
                verticalLineTo(8.0f)
                horizontalLineToRelative(16.0f)
                verticalLineTo(18.0f)
                close()
                moveTo(12.0f, 16.0f)
                curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
                horizontalLineToRelative(4.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
                curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
                horizontalLineToRelative(-4.0f)
                curveTo(12.45f, 17.0f, 12.0f, 16.55f, 12.0f, 16.0f)
                close()
                moveTo(6.79f, 9.71f)
                curveToRelative(0.39f, -0.39f, 1.02f, -0.39f, 1.41f, 0.0f)
                lineToRelative(2.59f, 2.59f)
                curveToRelative(0.39f, 0.39f, 0.39f, 1.02f, 0.0f, 1.41f)
                lineToRelative(-2.59f, 2.59f)
                curveToRelative(-0.39f, 0.39f, -1.02f, 0.39f, -1.41f, 0.0f)
                curveToRelative(-0.39f, -0.39f, -0.39f, -1.02f, 0.0f, -1.41f)
                lineTo(8.67f, 13.0f)
                lineToRelative(-1.88f, -1.88f)
                curveTo(6.4f, 10.73f, 6.4f, 10.1f, 6.79f, 9.71f)
                close()
            }
        }.also { _terminal = it }
    }

private var _tune: ImageVector? = null
val Icons.Rounded.Tune: ImageVector
    get() {
        _tune?.let { return it }
        return materialIcon(name = "Rounded.Tune") {
            materialPath {
                moveTo(3.0f, 18.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(5.0f)
                verticalLineToRelative(-2.0f)
                lineTo(4.0f, 17.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                close()
                moveTo(3.0f, 6.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(9.0f)
                lineTo(13.0f, 5.0f)
                lineTo(4.0f, 5.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                close()
                moveTo(13.0f, 20.0f)
                verticalLineToRelative(-1.0f)
                horizontalLineToRelative(7.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                reflectiveCurveToRelative(-0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineToRelative(-7.0f)
                verticalLineToRelative(-1.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                reflectiveCurveToRelative(-1.0f, 0.45f, -1.0f, 1.0f)
                verticalLineToRelative(4.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                reflectiveCurveToRelative(1.0f, -0.45f, 1.0f, -1.0f)
                close()
                moveTo(7.0f, 10.0f)
                verticalLineToRelative(1.0f)
                lineTo(4.0f, 11.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                reflectiveCurveToRelative(0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(3.0f)
                verticalLineToRelative(1.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                reflectiveCurveToRelative(1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineToRelative(-4.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                reflectiveCurveToRelative(-1.0f, 0.45f, -1.0f, 1.0f)
                close()
                moveTo(21.0f, 12.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineToRelative(-9.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(9.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                close()
                moveTo(16.0f, 9.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                lineTo(17.0f, 7.0f)
                horizontalLineToRelative(3.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                reflectiveCurveToRelative(-0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineToRelative(-3.0f)
                lineTo(17.0f, 4.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                reflectiveCurveToRelative(-1.0f, 0.45f, -1.0f, 1.0f)
                verticalLineToRelative(4.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                close()
            }
        }.also { _tune = it }
    }

private var _upload: ImageVector? = null
val Icons.Rounded.Upload: ImageVector
    get() {
        _upload?.let { return it }
        return materialIcon(name = "Rounded.Upload") {
            materialPath {
                moveTo(10.0f, 16.0f)
                horizontalLineToRelative(4.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineToRelative(-5.0f)
                horizontalLineToRelative(1.59f)
                curveToRelative(0.89f, 0.0f, 1.34f, -1.08f, 0.71f, -1.71f)
                lineTo(12.71f, 3.7f)
                curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0.0f)
                lineTo(6.71f, 8.29f)
                curveToRelative(-0.63f, 0.63f, -0.19f, 1.71f, 0.7f, 1.71f)
                lineTo(9.0f, 10.0f)
                verticalLineToRelative(5.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                close()
                moveTo(6.0f, 18.0f)
                horizontalLineToRelative(12.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
                reflectiveCurveToRelative(-0.45f, 1.0f, -1.0f, 1.0f)
                lineTo(6.0f, 20.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                reflectiveCurveToRelative(0.45f, -1.0f, 1.0f, -1.0f)
                close()
            }
        }.also { _upload = it }
    }

private var _visibility: ImageVector? = null
val Icons.Rounded.Visibility: ImageVector
    get() {
        _visibility?.let { return it }
        return materialIcon(name = "Rounded.Visibility") {
            materialPath {
                moveTo(12.0f, 4.0f)
                curveTo(7.0f, 4.0f, 2.73f, 7.11f, 1.0f, 11.5f)
                curveTo(2.73f, 15.89f, 7.0f, 19.0f, 12.0f, 19.0f)
                reflectiveCurveToRelative(9.27f, -3.11f, 11.0f, -7.5f)
                curveTo(21.27f, 7.11f, 17.0f, 4.0f, 12.0f, 4.0f)
                close()
                moveTo(12.0f, 16.5f)
                curveToRelative(-2.76f, 0.0f, -5.0f, -2.24f, -5.0f, -5.0f)
                reflectiveCurveToRelative(2.24f, -5.0f, 5.0f, -5.0f)
                reflectiveCurveToRelative(5.0f, 2.24f, 5.0f, 5.0f)
                reflectiveCurveToRelative(-2.24f, 5.0f, -5.0f, 5.0f)
                close()
                moveTo(12.0f, 8.5f)
                curveToRelative(-1.66f, 0.0f, -3.0f, 1.34f, -3.0f, 3.0f)
                reflectiveCurveToRelative(1.34f, 3.0f, 3.0f, 3.0f)
                reflectiveCurveToRelative(3.0f, -1.34f, 3.0f, -3.0f)
                reflectiveCurveToRelative(-1.34f, -3.0f, -3.0f, -3.0f)
                close()
            }
        }.also { _visibility = it }
    }

private var _visibilityOff: ImageVector? = null
val Icons.Rounded.VisibilityOff: ImageVector
    get() {
        _visibilityOff?.let { return it }
        return materialIcon(name = "Rounded.VisibilityOff") {
            materialPath {
                moveTo(12.0f, 6.5f)
                curveToRelative(2.76f, 0.0f, 5.0f, 2.24f, 5.0f, 5.0f)
                curveToRelative(0.0f, 0.51f, -0.1f, 1.0f, -0.24f, 1.46f)
                lineToRelative(3.06f, 3.06f)
                curveToRelative(1.39f, -1.23f, 2.49f, -2.77f, 3.18f, -4.53f)
                curveTo(21.27f, 7.11f, 17.0f, 4.0f, 12.0f, 4.0f)
                curveToRelative(-1.27f, 0.0f, -2.49f, 0.2f, -3.64f, 0.57f)
                lineToRelative(2.17f, 2.17f)
                curveToRelative(0.47f, -0.14f, 0.96f, -0.24f, 1.47f, -0.24f)
                close()
                moveTo(2.71f, 3.16f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0.0f, 1.41f)
                lineToRelative(1.97f, 1.97f)
                curveTo(3.06f, 7.83f, 1.77f, 9.53f, 1.0f, 11.5f)
                curveTo(2.73f, 15.89f, 7.0f, 19.0f, 12.0f, 19.0f)
                curveToRelative(1.52f, 0.0f, 2.97f, -0.3f, 4.31f, -0.82f)
                lineToRelative(2.72f, 2.72f)
                curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0.0f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0.0f, -1.41f)
                lineTo(4.13f, 3.16f)
                curveToRelative(-0.39f, -0.39f, -1.03f, -0.39f, -1.42f, 0.0f)
                close()
                moveTo(12.0f, 16.5f)
                curveToRelative(-2.76f, 0.0f, -5.0f, -2.24f, -5.0f, -5.0f)
                curveToRelative(0.0f, -0.77f, 0.18f, -1.5f, 0.49f, -2.14f)
                lineToRelative(1.57f, 1.57f)
                curveToRelative(-0.03f, 0.18f, -0.06f, 0.37f, -0.06f, 0.57f)
                curveToRelative(0.0f, 1.66f, 1.34f, 3.0f, 3.0f, 3.0f)
                curveToRelative(0.2f, 0.0f, 0.38f, -0.03f, 0.57f, -0.07f)
                lineTo(14.14f, 16.0f)
                curveToRelative(-0.65f, 0.32f, -1.37f, 0.5f, -2.14f, 0.5f)
                close()
                moveTo(14.97f, 11.17f)
                curveToRelative(-0.15f, -1.4f, -1.25f, -2.49f, -2.64f, -2.64f)
                lineToRelative(2.64f, 2.64f)
                close()
            }
        }.also { _visibilityOff = it }
    }

private var _warningAmber: ImageVector? = null
val Icons.Rounded.WarningAmber: ImageVector
    get() {
        _warningAmber?.let { return it }
        return materialIcon(name = "Rounded.WarningAmber") {
            materialPath {
                moveTo(12.0f, 5.99f)
                lineTo(19.53f, 19.0f)
                lineTo(4.47f, 19.0f)
                lineTo(12.0f, 5.99f)
                moveTo(2.74f, 18.0f)
                curveToRelative(-0.77f, 1.33f, 0.19f, 3.0f, 1.73f, 3.0f)
                horizontalLineToRelative(15.06f)
                curveToRelative(1.54f, 0.0f, 2.5f, -1.67f, 1.73f, -3.0f)
                lineTo(13.73f, 4.99f)
                curveToRelative(-0.77f, -1.33f, -2.69f, -1.33f, -3.46f, 0.0f)
                lineTo(2.74f, 18.0f)
                close()
                moveTo(11.0f, 11.0f)
                verticalLineToRelative(2.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                reflectiveCurveToRelative(1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineToRelative(-2.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                reflectiveCurveToRelative(-1.0f, 0.45f, -1.0f, 1.0f)
                close()
                moveTo(11.0f, 16.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(-2.0f)
                close()
            }
        }.also { _warningAmber = it }
    }

private var _workOutline: ImageVector? = null
val Icons.Rounded.WorkOutline: ImageVector
    get() {
        _workOutline?.let { return it }
        return materialIcon(name = "Rounded.WorkOutline") {
            materialPath {
                moveTo(14.0f, 6.0f)
                lineTo(14.0f, 4.0f)
                horizontalLineToRelative(-4.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(4.0f)
                close()
                moveTo(4.0f, 9.0f)
                verticalLineToRelative(9.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(14.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                lineTo(20.0f, 9.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                lineTo(5.0f, 8.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                close()
                moveTo(20.0f, 6.0f)
                curveToRelative(1.11f, 0.0f, 2.0f, 0.89f, 2.0f, 2.0f)
                verticalLineToRelative(11.0f)
                curveToRelative(0.0f, 1.11f, -0.89f, 2.0f, -2.0f, 2.0f)
                lineTo(4.0f, 21.0f)
                curveToRelative(-1.11f, 0.0f, -2.0f, -0.89f, -2.0f, -2.0f)
                lineToRelative(0.01f, -11.0f)
                curveToRelative(0.0f, -1.11f, 0.88f, -2.0f, 1.99f, -2.0f)
                horizontalLineToRelative(4.0f)
                lineTo(8.0f, 4.0f)
                curveToRelative(0.0f, -1.11f, 0.89f, -2.0f, 2.0f, -2.0f)
                horizontalLineToRelative(4.0f)
                curveToRelative(1.11f, 0.0f, 2.0f, 0.89f, 2.0f, 2.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(4.0f)
                close()
            }
        }.also { _workOutline = it }
    }

private var _apps: ImageVector? = null
val Icons.Rounded.Apps: ImageVector
    get() {
        _apps?.let { return it }
        return materialIcon(name = "Rounded.Apps") {
            materialPath {
                moveTo(4.0f, 8.0f)
                horizontalLineToRelative(4.0f)
                verticalLineTo(4.0f)
                horizontalLineTo(4.0f)
                verticalLineTo(8.0f)
                close()
                moveTo(10.0f, 20.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(-4.0f)
                horizontalLineToRelative(-4.0f)
                verticalLineTo(20.0f)
                close()
                moveTo(4.0f, 20.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(-4.0f)
                horizontalLineTo(4.0f)
                verticalLineTo(20.0f)
                close()
                moveTo(4.0f, 14.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(-4.0f)
                horizontalLineTo(4.0f)
                verticalLineTo(14.0f)
                close()
                moveTo(10.0f, 14.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(-4.0f)
                horizontalLineToRelative(-4.0f)
                verticalLineTo(14.0f)
                close()
                moveTo(16.0f, 4.0f)
                verticalLineToRelative(4.0f)
                horizontalLineToRelative(4.0f)
                verticalLineTo(4.0f)
                horizontalLineTo(16.0f)
                close()
                moveTo(10.0f, 8.0f)
                horizontalLineToRelative(4.0f)
                verticalLineTo(4.0f)
                horizontalLineToRelative(-4.0f)
                verticalLineTo(8.0f)
                close()
                moveTo(16.0f, 14.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(-4.0f)
                horizontalLineToRelative(-4.0f)
                verticalLineTo(14.0f)
                close()
                moveTo(16.0f, 20.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(-4.0f)
                horizontalLineToRelative(-4.0f)
                verticalLineTo(20.0f)
                close()
            }
        }.also { _apps = it }
    }

private var _installMobile: ImageVector? = null
val Icons.Rounded.InstallMobile: ImageVector
    get() {
        _installMobile?.let { return it }
        return materialIcon(name = "Rounded.InstallMobile") {
            materialPath {
                moveTo(18.71f, 13.29f)
                lineToRelative(3.59f, -3.59f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0.0f, -1.41f)
                curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0.0f)
                lineTo(19.0f, 10.17f)
                verticalLineTo(4.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                reflectiveCurveToRelative(-1.0f, 0.45f, -1.0f, 1.0f)
                verticalLineToRelative(6.17f)
                lineToRelative(-1.89f, -1.88f)
                curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0.0f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0.0f, 1.41f)
                lineToRelative(3.59f, 3.59f)
                curveTo(17.69f, 13.68f, 18.32f, 13.68f, 18.71f, 13.29f)
                close()
                moveTo(17.0f, 18.0f)
                horizontalLineTo(7.0f)
                verticalLineTo(6.0f)
                horizontalLineToRelative(7.0f)
                verticalLineTo(1.0f)
                horizontalLineTo(7.0f)
                curveTo(5.9f, 1.0f, 5.0f, 1.9f, 5.0f, 3.0f)
                verticalLineToRelative(18.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(10.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineToRelative(-5.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineTo(18.0f)
                close()
            }
        }.also { _installMobile = it }
    }
