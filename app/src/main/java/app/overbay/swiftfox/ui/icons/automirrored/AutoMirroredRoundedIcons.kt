package app.overbay.swiftfox.ui.icons.automirrored

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector

private var _logout: ImageVector? = null
val Icons.AutoMirrored.Rounded.Logout: ImageVector
    get() {
        _logout?.let { return it }
        return materialIcon(name = "AutoMirrored.Rounded.Logout", autoMirror = true) {
            materialPath {
                moveTo(5.0f, 5.0f)
                horizontalLineToRelative(6.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineToRelative(0.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineTo(5.0f)
                curveTo(3.9f, 3.0f, 3.0f, 3.9f, 3.0f, 5.0f)
                verticalLineToRelative(14.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(6.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineToRelative(0.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineTo(5.0f)
                verticalLineTo(5.0f)
                close()
            }
            materialPath {
                moveTo(20.65f, 11.65f)
                lineToRelative(-2.79f, -2.79f)
                curveTo(17.54f, 8.54f, 17.0f, 8.76f, 17.0f, 9.21f)
                verticalLineTo(11.0f)
                horizontalLineToRelative(-7.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                verticalLineToRelative(0.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(7.0f)
                verticalLineToRelative(1.79f)
                curveToRelative(0.0f, 0.45f, 0.54f, 0.67f, 0.85f, 0.35f)
                lineToRelative(2.79f, -2.79f)
                curveTo(20.84f, 12.16f, 20.84f, 11.84f, 20.65f, 11.65f)
                close()
            }
        }.also { _logout = it }
    }

private var _menuBook: ImageVector? = null
val Icons.AutoMirrored.Rounded.MenuBook: ImageVector
    get() {
        _menuBook?.let { return it }
        return materialIcon(name = "AutoMirrored.Rounded.MenuBook", autoMirror = true) {
            materialPath {
                moveTo(17.5f, 4.5f)
                curveToRelative(-1.95f, 0.0f, -4.05f, 0.4f, -5.5f, 1.5f)
                curveToRelative(-1.45f, -1.1f, -3.55f, -1.5f, -5.5f, -1.5f)
                curveToRelative(-1.45f, 0.0f, -2.99f, 0.22f, -4.28f, 0.79f)
                curveTo(1.49f, 5.62f, 1.0f, 6.33f, 1.0f, 7.14f)
                lineToRelative(0.0f, 11.28f)
                curveToRelative(0.0f, 1.3f, 1.22f, 2.26f, 2.48f, 1.94f)
                curveTo(4.46f, 20.11f, 5.5f, 20.0f, 6.5f, 20.0f)
                curveToRelative(1.56f, 0.0f, 3.22f, 0.26f, 4.56f, 0.92f)
                curveToRelative(0.6f, 0.3f, 1.28f, 0.3f, 1.87f, 0.0f)
                curveToRelative(1.34f, -0.67f, 3.0f, -0.92f, 4.56f, -0.92f)
                curveToRelative(1.0f, 0.0f, 2.04f, 0.11f, 3.02f, 0.36f)
                curveToRelative(1.26f, 0.33f, 2.48f, -0.63f, 2.48f, -1.94f)
                lineToRelative(0.0f, -11.28f)
                curveToRelative(0.0f, -0.81f, -0.49f, -1.52f, -1.22f, -1.85f)
                curveTo(20.49f, 4.72f, 18.95f, 4.5f, 17.5f, 4.5f)
                close()
                moveTo(21.0f, 17.23f)
                curveToRelative(0.0f, 0.63f, -0.58f, 1.09f, -1.2f, 0.98f)
                curveToRelative(-0.75f, -0.14f, -1.53f, -0.2f, -2.3f, -0.2f)
                curveToRelative(-1.7f, 0.0f, -4.15f, 0.65f, -5.5f, 1.5f)
                verticalLineTo(8.0f)
                curveToRelative(1.35f, -0.85f, 3.8f, -1.5f, 5.5f, -1.5f)
                curveToRelative(0.92f, 0.0f, 1.83f, 0.09f, 2.7f, 0.28f)
                curveToRelative(0.46f, 0.1f, 0.8f, 0.51f, 0.8f, 0.98f)
                verticalLineTo(17.23f)
                close()
            }
            materialPath {
                moveTo(13.98f, 11.01f)
                curveToRelative(-0.32f, 0.0f, -0.61f, -0.2f, -0.71f, -0.52f)
                curveToRelative(-0.13f, -0.39f, 0.09f, -0.82f, 0.48f, -0.94f)
                curveToRelative(1.54f, -0.5f, 3.53f, -0.66f, 5.36f, -0.45f)
                curveToRelative(0.41f, 0.05f, 0.71f, 0.42f, 0.66f, 0.83f)
                curveToRelative(-0.05f, 0.41f, -0.42f, 0.71f, -0.83f, 0.66f)
                curveToRelative(-1.62f, -0.19f, -3.39f, -0.04f, -4.73f, 0.39f)
                curveTo(14.13f, 10.99f, 14.05f, 11.01f, 13.98f, 11.01f)
                close()
            }
            materialPath {
                moveTo(13.98f, 13.67f)
                curveToRelative(-0.32f, 0.0f, -0.61f, -0.2f, -0.71f, -0.52f)
                curveToRelative(-0.13f, -0.39f, 0.09f, -0.82f, 0.48f, -0.94f)
                curveToRelative(1.53f, -0.5f, 3.53f, -0.66f, 5.36f, -0.45f)
                curveToRelative(0.41f, 0.05f, 0.71f, 0.42f, 0.66f, 0.83f)
                curveToRelative(-0.05f, 0.41f, -0.42f, 0.71f, -0.83f, 0.66f)
                curveToRelative(-1.62f, -0.19f, -3.39f, -0.04f, -4.73f, 0.39f)
                curveTo(14.13f, 13.66f, 14.05f, 13.67f, 13.98f, 13.67f)
                close()
            }
            materialPath {
                moveTo(13.98f, 16.33f)
                curveToRelative(-0.32f, 0.0f, -0.61f, -0.2f, -0.71f, -0.52f)
                curveToRelative(-0.13f, -0.39f, 0.09f, -0.82f, 0.48f, -0.94f)
                curveToRelative(1.53f, -0.5f, 3.53f, -0.66f, 5.36f, -0.45f)
                curveToRelative(0.41f, 0.05f, 0.71f, 0.42f, 0.66f, 0.83f)
                curveToRelative(-0.05f, 0.41f, -0.42f, 0.7f, -0.83f, 0.66f)
                curveToRelative(-1.62f, -0.19f, -3.39f, -0.04f, -4.73f, 0.39f)
                curveTo(14.13f, 16.32f, 14.05f, 16.33f, 13.98f, 16.33f)
                close()
            }
        }.also { _menuBook = it }
    }

private var _openInNew: ImageVector? = null
val Icons.AutoMirrored.Rounded.OpenInNew: ImageVector
    get() {
        _openInNew?.let { return it }
        return materialIcon(name = "AutoMirrored.Rounded.OpenInNew", autoMirror = true) {
            materialPath {
                moveTo(18.0f, 19.0f)
                horizontalLineTo(6.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                verticalLineTo(6.0f)
                curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
                horizontalLineToRelative(5.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                reflectiveCurveToRelative(-0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineTo(5.0f)
                curveToRelative(-1.11f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(14.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(14.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineToRelative(-6.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                reflectiveCurveToRelative(-1.0f, 0.45f, -1.0f, 1.0f)
                verticalLineToRelative(5.0f)
                curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
                close()
                moveTo(14.0f, 4.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(2.59f)
                lineToRelative(-9.13f, 9.13f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0.0f, 1.41f)
                curveToRelative(0.39f, 0.39f, 1.02f, 0.39f, 1.41f, 0.0f)
                lineTo(19.0f, 6.41f)
                verticalLineTo(9.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                reflectiveCurveToRelative(1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineTo(4.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineToRelative(-5.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                close()
            }
        }.also { _openInNew = it }
    }

private var _sendToMobile: ImageVector? = null
val Icons.AutoMirrored.Rounded.SendToMobile: ImageVector
    get() {
        _sendToMobile?.let { return it }
        return materialIcon(name = "AutoMirrored.Rounded.SendToMobile", autoMirror = true) {
            materialPath {
                moveTo(17.0f, 18.0f)
                horizontalLineTo(7.0f)
                verticalLineTo(6.0f)
                horizontalLineToRelative(10.0f)
                verticalLineToRelative(0.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(0.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineTo(3.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                lineTo(7.0f, 1.01f)
                curveTo(5.9f, 1.01f, 5.0f, 1.9f, 5.0f, 3.0f)
                verticalLineToRelative(18.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(10.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineToRelative(-3.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineToRelative(0.0f)
                curveTo(17.45f, 17.0f, 17.0f, 17.45f, 17.0f, 18.0f)
                lineTo(17.0f, 18.0f)
                close()
            }
            materialPath {
                moveTo(21.65f, 11.65f)
                lineToRelative(-2.79f, -2.79f)
                curveTo(18.54f, 8.54f, 18.0f, 8.76f, 18.0f, 9.21f)
                verticalLineTo(11.0f)
                horizontalLineToRelative(-4.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                verticalLineToRelative(0.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(1.79f)
                curveToRelative(0.0f, 0.45f, 0.54f, 0.67f, 0.85f, 0.35f)
                lineToRelative(2.79f, -2.79f)
                curveTo(21.84f, 12.16f, 21.84f, 11.84f, 21.65f, 11.65f)
                close()
            }
        }.also { _sendToMobile = it }
    }

private var _undo: ImageVector? = null
val Icons.AutoMirrored.Rounded.Undo: ImageVector
    get() {
        _undo?.let { return it }
        return materialIcon(name = "AutoMirrored.Rounded.Undo", autoMirror = true) {
            materialPath {
                moveTo(12.5f, 8.0f)
                curveToRelative(-2.65f, 0.0f, -5.05f, 0.99f, -6.9f, 2.6f)
                lineTo(3.71f, 8.71f)
                curveTo(3.08f, 8.08f, 2.0f, 8.52f, 2.0f, 9.41f)
                verticalLineTo(15.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(5.59f)
                curveToRelative(0.89f, 0.0f, 1.34f, -1.08f, 0.71f, -1.71f)
                lineToRelative(-1.91f, -1.91f)
                curveToRelative(1.39f, -1.16f, 3.16f, -1.88f, 5.12f, -1.88f)
                curveToRelative(3.16f, 0.0f, 5.89f, 1.84f, 7.19f, 4.5f)
                curveToRelative(0.27f, 0.56f, 0.91f, 0.84f, 1.5f, 0.64f)
                curveToRelative(0.71f, -0.23f, 1.07f, -1.04f, 0.75f, -1.72f)
                curveTo(20.23f, 10.42f, 16.65f, 8.0f, 12.5f, 8.0f)
                close()
            }
        }.also { _undo = it }
    }

private var _viewSidebar: ImageVector? = null
val Icons.AutoMirrored.Rounded.ViewSidebar: ImageVector
    get() {
        _viewSidebar?.let { return it }
        return materialIcon(name = "AutoMirrored.Rounded.ViewSidebar", autoMirror = true) {
            materialPath {
                moveTo(15.0f, 20.0f)
                horizontalLineTo(3.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                verticalLineTo(5.0f)
                curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
                horizontalLineToRelative(12.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
                verticalLineToRelative(14.0f)
                curveTo(16.0f, 19.55f, 15.55f, 20.0f, 15.0f, 20.0f)
                close()
                moveTo(19.0f, 8.0f)
                horizontalLineToRelative(2.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineTo(5.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineToRelative(-2.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                verticalLineToRelative(2.0f)
                curveTo(18.0f, 7.55f, 18.45f, 8.0f, 19.0f, 8.0f)
                close()
                moveTo(19.0f, 20.0f)
                horizontalLineToRelative(2.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineToRelative(-2.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineToRelative(-2.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                verticalLineToRelative(2.0f)
                curveTo(18.0f, 19.55f, 18.45f, 20.0f, 19.0f, 20.0f)
                close()
                moveTo(19.0f, 14.0f)
                horizontalLineToRelative(2.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineToRelative(-2.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                horizontalLineToRelative(-2.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                verticalLineToRelative(2.0f)
                curveTo(18.0f, 13.55f, 18.45f, 14.0f, 19.0f, 14.0f)
                close()
            }
        }.also { _viewSidebar = it }
    }

private var _volumeUp: ImageVector? = null
val Icons.AutoMirrored.Rounded.VolumeUp: ImageVector
    get() {
        _volumeUp?.let { return it }
        return materialIcon(name = "AutoMirrored.Rounded.VolumeUp", autoMirror = true) {
            materialPath {
                moveTo(3.0f, 10.0f)
                verticalLineToRelative(4.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(3.0f)
                lineToRelative(3.29f, 3.29f)
                curveToRelative(0.63f, 0.63f, 1.71f, 0.18f, 1.71f, -0.71f)
                lineTo(12.0f, 6.41f)
                curveToRelative(0.0f, -0.89f, -1.08f, -1.34f, -1.71f, -0.71f)
                lineTo(7.0f, 9.0f)
                lineTo(4.0f, 9.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                close()
                moveTo(16.5f, 12.0f)
                curveToRelative(0.0f, -1.77f, -1.02f, -3.29f, -2.5f, -4.03f)
                verticalLineToRelative(8.05f)
                curveToRelative(1.48f, -0.73f, 2.5f, -2.25f, 2.5f, -4.02f)
                close()
                moveTo(14.0f, 4.45f)
                verticalLineToRelative(0.2f)
                curveToRelative(0.0f, 0.38f, 0.25f, 0.71f, 0.6f, 0.85f)
                curveTo(17.18f, 6.53f, 19.0f, 9.06f, 19.0f, 12.0f)
                reflectiveCurveToRelative(-1.82f, 5.47f, -4.4f, 6.5f)
                curveToRelative(-0.36f, 0.14f, -0.6f, 0.47f, -0.6f, 0.85f)
                verticalLineToRelative(0.2f)
                curveToRelative(0.0f, 0.63f, 0.63f, 1.07f, 1.21f, 0.85f)
                curveTo(18.6f, 19.11f, 21.0f, 15.84f, 21.0f, 12.0f)
                reflectiveCurveToRelative(-2.4f, -7.11f, -5.79f, -8.4f)
                curveToRelative(-0.58f, -0.23f, -1.21f, 0.22f, -1.21f, 0.85f)
                close()
            }
        }.also { _volumeUp = it }
    }
