package app.overbay.swiftfox.features.webapps

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import app.overbay.swiftfox.MainActivity
import app.overbay.swiftfox.data.db.WebAppEntity
import app.overbay.swiftfox.ui.webapps.WebAppActivity

/**
 * Pins, updates, launches, and disables Android Home Screen shortcuts for installed PWAs
 * (launching into standalone [WebAppActivity]) and regular web shortcuts (launching in [MainActivity]).
 */
object WebAppShortcutManager {

    const val ACTION_LAUNCH_WEB_APP = "app.overbay.swiftfox.action.LAUNCH_WEB_APP"
    const val EXTRA_WEB_APP_ID = "app.overbay.swiftfox.extra.WEB_APP_ID"
    const val EXTRA_OPEN_CONTROLS = "app.overbay.swiftfox.extra.OPEN_CONTROLS"

    fun isPinSupported(context: Context): Boolean =
        ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    fun buildLaunchIntent(context: Context, webApp: WebAppEntity): Intent {
        return if (webApp.openAsStandalone) {
            Intent(context, WebAppActivity::class.java).apply {
                action = ACTION_LAUNCH_WEB_APP
                data = Uri.parse("swiftfox-pwa://app/${webApp.id}")
                putExtra(EXTRA_WEB_APP_ID, webApp.id)
                addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
            }
        } else {
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse(webApp.startUrl)
                `package` = context.packageName
                putExtra(EXTRA_WEB_APP_ID, webApp.id)
            }
        }
    }

    fun launchWebApp(context: Context, webApp: WebAppEntity): Boolean {
        val intent = buildLaunchIntent(context, webApp).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun buildShortcutInfo(
        context: Context,
        webApp: WebAppEntity,
        iconBitmap: Bitmap,
        iconHelper: WebAppIconHelper
    ): ShortcutInfoCompat {
        val label = webApp.shortName.ifBlank { webApp.name }.ifBlank { webApp.host }.take(32)
        val longLabel = webApp.name.ifBlank { label }.take(64)
        val isMaskable = webApp.iconMaskable && webApp.customIcon.isNullOrBlank()
        val adaptiveIcon = iconHelper.buildAdaptiveShortcutIcon(
            source = iconBitmap,
            maskable = isMaskable,
            backgroundColorHex = webApp.backgroundColorHex ?: webApp.themeColorHex
        )
        return ShortcutInfoCompat.Builder(context, webApp.pinnedShortcutId)
            .setShortLabel(label)
            .setLongLabel(longLabel)
            .setIcon(adaptiveIcon)
            .setIntent(buildLaunchIntent(context, webApp))
            .build()
    }

    fun requestPinShortcut(
        context: Context,
        webApp: WebAppEntity,
        iconBitmap: Bitmap,
        iconHelper: WebAppIconHelper
    ): Boolean {
        if (!isPinSupported(context)) return false
        val info = buildShortcutInfo(context, webApp, iconBitmap, iconHelper)
        return runCatching {
            ShortcutManagerCompat.requestPinShortcut(context, info, null)
        }.getOrDefault(false)
    }

    fun updatePinnedShortcut(
        context: Context,
        webApp: WebAppEntity,
        iconBitmap: Bitmap,
        iconHelper: WebAppIconHelper
    ) {
        val info = buildShortcutInfo(context, webApp, iconBitmap, iconHelper)
        runCatching {
            ShortcutManagerCompat.updateShortcuts(context, listOf(info))
        }
    }

    fun disablePinnedShortcut(context: Context, pinnedShortcutId: String) {
        runCatching {
            ShortcutManagerCompat.disableShortcuts(
                context,
                listOf(pinnedShortcutId),
                "Removed from Swiftfox Web Apps"
            )
        }
        runCatching {
            ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(pinnedShortcutId))
        }
    }
}
