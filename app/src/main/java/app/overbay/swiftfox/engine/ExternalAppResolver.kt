package app.overbay.swiftfox.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri

data class ExternalAppTarget(
    val packageName: String,
    val appLabel: String,
    val launchIntent: Intent
)

/**
 * Resolves dedicated non-browser Android apps capable of opening an HTTP(S) URL.
 *
 * Uses a short-lived cache of generic browser packages (discovered via a bogus `https://example.invalid/`
 * probe) so browsers and Swiftfox itself are excluded when determining whether a website can open in
 * a native installed app.
 */
object ExternalAppResolver {

    private const val BROWSER_CACHE_TTL_MS = 60_000L

    @Volatile
    private var browserPackagesCache: Set<String>? = null

    @Volatile
    private var browserPackagesCachedAtMs: Long = 0L

    fun resolveForHttpUrl(context: Context, url: String): ExternalAppTarget? {
        val trimmed = url.trim()
        if (!isHttpSchemeUrl(trimmed)) {
            return null
        }
        val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
        return resolveForHttpUri(context, uri)
    }

    fun resolveForHttpUri(context: Context, uri: Uri): ExternalAppTarget? {
        val scheme = uri.scheme
        if (!scheme.equals("http", ignoreCase = true) && !scheme.equals("https", ignoreCase = true)) {
            return null
        }
        if (uri.host.isNullOrBlank()) return null

        val pm = context.packageManager
        val viewIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        val dedicatedApp = findDedicatedNativeApp(context, pm, viewIntent) ?: return null
        val pkg = dedicatedApp.activityInfo?.packageName?.takeIf { it.isNotBlank() } ?: return null
        val appLabel = runCatching {
            dedicatedApp.loadLabel(pm).toString()
        }.getOrNull().orEmpty().trim().ifBlank { "App" }

        val nonBrowserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            setPackage(pkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER)
        }
        return ExternalAppTarget(
            packageName = pkg,
            appLabel = appLabel,
            launchIntent = nonBrowserIntent
        )
    }

    fun findDedicatedNativeApp(
        context: Context,
        pm: PackageManager,
        intent: Intent
    ): ResolveInfo? {
        val candidates = runCatching {
            pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }.getOrDefault(emptyList())
        if (candidates.isEmpty()) return null

        val genericBrowserPackages = cachedGenericBrowserPackages(pm)
        val selfPkg = context.packageName
        val selectedPkg = selectDedicatedPackage(
            candidatePackages = candidates.mapNotNull { it.activityInfo?.packageName },
            selfPackage = selfPkg,
            genericBrowserPackages = genericBrowserPackages
        ) ?: return null

        return candidates.firstOrNull { it.activityInfo?.packageName == selectedPkg }
    }

    internal fun isHttpSchemeUrl(url: String): Boolean {
        val trimmed = url.trim()
        return trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
    }

    internal fun selectDedicatedPackage(
        candidatePackages: List<String>,
        selfPackage: String,
        genericBrowserPackages: Set<String>
    ): String? = candidatePackages.firstOrNull { pkg ->
        pkg.isNotBlank() &&
            pkg != selfPackage &&
            pkg !in genericBrowserPackages
    }

    internal fun cachedGenericBrowserPackages(pm: PackageManager): Set<String> {
        val now = System.currentTimeMillis()
        val cached = browserPackagesCache
        if (cached != null && (now - browserPackagesCachedAtMs) < BROWSER_CACHE_TTL_MS) {
            return cached
        }
        val fresh = runCatching {
            val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.invalid/")).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
            pm.queryIntentActivities(probe, PackageManager.MATCH_ALL)
                .mapNotNull { it.activityInfo?.packageName }
                .toSet()
        }.getOrNull() ?: return cached ?: emptySet()
        browserPackagesCache = fresh
        browserPackagesCachedAtMs = now
        return fresh
    }
}
