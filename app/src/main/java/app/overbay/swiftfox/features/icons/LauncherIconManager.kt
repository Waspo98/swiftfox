package app.overbay.swiftfox.features.icons

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import app.overbay.swiftfox.data.prefs.AppIconPalette
import app.overbay.swiftfox.data.prefs.ResolvedAppIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Synchronizes Android launcher `<activity-alias>` components with the resolved app icon choice.
 */
object LauncherIconManager {

    internal const val ALIAS_DEFAULT_DARK = "app.overbay.swiftfox.LauncherDefaultDark"
    internal const val ALIAS_DEFAULT_LIGHT = "app.overbay.swiftfox.LauncherDefaultLight"
    internal const val ALIAS_AMBER_DARK = "app.overbay.swiftfox.LauncherAmberDark"
    internal const val ALIAS_AMBER_LIGHT = "app.overbay.swiftfox.LauncherAmberLight"
    internal const val ALIAS_INDIGO_DARK = "app.overbay.swiftfox.LauncherIndigoDark"
    internal const val ALIAS_INDIGO_LIGHT = "app.overbay.swiftfox.LauncherIndigoLight"
    internal const val ALIAS_FOREST_DARK = "app.overbay.swiftfox.LauncherForestDark"
    internal const val ALIAS_FOREST_LIGHT = "app.overbay.swiftfox.LauncherForestLight"
    internal const val ALIAS_MONOCHROME_DARK = "app.overbay.swiftfox.LauncherMonochromeDark"
    internal const val ALIAS_MONOCHROME_LIGHT = "app.overbay.swiftfox.LauncherMonochromeLight"

    internal val ALL_ALIAS_NAMES = listOf(
        ALIAS_DEFAULT_DARK,
        ALIAS_DEFAULT_LIGHT,
        ALIAS_AMBER_DARK,
        ALIAS_AMBER_LIGHT,
        ALIAS_INDIGO_DARK,
        ALIAS_INDIGO_LIGHT,
        ALIAS_FOREST_DARK,
        ALIAS_FOREST_LIGHT,
        ALIAS_MONOCHROME_DARK,
        ALIAS_MONOCHROME_LIGHT
    )

    private val syncMutex = Mutex()

    fun aliasNameFor(resolved: ResolvedAppIcon): String {
        if (resolved.usesChannelDefaultAlias || resolved.palette == AppIconPalette.BETA_VIOLET) {
            return if (resolved.isLightBackground) ALIAS_DEFAULT_LIGHT else ALIAS_DEFAULT_DARK
        }
        return when (resolved.palette) {
            AppIconPalette.FOX_AMBER -> if (resolved.isLightBackground) ALIAS_AMBER_LIGHT else ALIAS_AMBER_DARK
            AppIconPalette.BETA_VIOLET -> if (resolved.isLightBackground) ALIAS_DEFAULT_LIGHT else ALIAS_DEFAULT_DARK
            AppIconPalette.MIDNIGHT_INDIGO -> if (resolved.isLightBackground) ALIAS_INDIGO_LIGHT else ALIAS_INDIGO_DARK
            AppIconPalette.FOREST -> if (resolved.isLightBackground) ALIAS_FOREST_LIGHT else ALIAS_FOREST_DARK
            AppIconPalette.MONOCHROME -> if (resolved.isLightBackground) ALIAS_MONOCHROME_LIGHT else ALIAS_MONOCHROME_DARK
        }
    }

    suspend fun syncIcon(context: Context, resolved: ResolvedAppIcon) {
        val appContext = context.applicationContext
        val targetAlias = aliasNameFor(resolved)
        syncMutex.withLock {
            withContext(Dispatchers.IO) {
                applyAliasState(appContext, targetAlias)
            }
        }
    }

    private fun applyAliasState(context: Context, targetAlias: String) {
        val pm = context.packageManager
        val pkg = context.packageName
        val currentStates = ALL_ALIAS_NAMES.associateWith { alias ->
            runCatching {
                pm.getComponentEnabledSetting(ComponentName(pkg, alias))
            }.getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
        }

        val targetAlreadyEnabled = isEffectivelyEnabled(
            state = currentStates[targetAlias] ?: PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
            aliasName = targetAlias
        )
        val othersDisabled = ALL_ALIAS_NAMES.all { alias ->
            alias == targetAlias || !isEffectivelyEnabled(
                state = currentStates[alias] ?: PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                aliasName = alias
            )
        }
        if (targetAlreadyEnabled && othersDisabled) return

        // Enable target first so the package never has zero active launcher aliases mid-switch.
        runCatching {
            pm.setComponentEnabledSetting(
                ComponentName(pkg, targetAlias),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        }

        for (alias in ALL_ALIAS_NAMES) {
            if (alias == targetAlias) continue
            val currentState = currentStates[alias] ?: PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            if (isEffectivelyEnabled(currentState, alias)) {
                runCatching {
                    pm.setComponentEnabledSetting(
                        ComponentName(pkg, alias),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
            }
        }
    }

    private fun isEffectivelyEnabled(state: Int, aliasName: String): Boolean = when (state) {
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> aliasName == ALIAS_DEFAULT_DARK
        else -> false
    }
}
