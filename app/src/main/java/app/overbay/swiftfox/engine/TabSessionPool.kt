package app.overbay.swiftfox.engine

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewFeature
import app.overbay.swiftfox.features.darkreader.DarkReaderInjector

class TabSessionPool(
    private val maxWarmSessions: Int = 5,
    private val maxThumbnailCacheBytes: Long = DEFAULT_MAX_THUMBNAIL_BYTES,
    private val maxHibernatedBundles: Int = DEFAULT_MAX_HIBERNATED_BUNDLES
) {
    private val lock = Any()
    private val warmSessions = LinkedHashMap<String, BrowserSession>(maxWarmSessions, 0.75f, true)
    private val hibernatedBundles = LinkedHashMap<String, Bundle>(16, 0.75f, true)
    private val thumbnails = LinkedHashMap<String, Bitmap>(16, 0.75f, true)
    private var thumbnailCacheBytes: Long = 0L
    private var currentHostContext: Context? = null
    @Volatile
    private var currentActiveTabId: String? = null
    @Volatile
    var onThumbnailsEvicted: ((Set<String>) -> Unit)? = null
    @Volatile
    var onThumbnailCaptured: ((String) -> Unit)? = null

    private fun storeHibernatedBundleLocked(tabId: String, bundle: Bundle) {
        hibernatedBundles[tabId] = bundle
        while (hibernatedBundles.size > maxHibernatedBundles) {
            val eldestKey = hibernatedBundles.entries.firstOrNull { it.key != currentActiveTabId }?.key
                ?: hibernatedBundles.entries.firstOrNull()?.key
                ?: break
            hibernatedBundles.remove(eldestKey)
        }
    }

    private fun putThumbnailLocked(tabId: String, bitmap: Bitmap): Set<String> {
        val addedBytes = bitmapByteSize(bitmap)
        thumbnails.remove(tabId)?.let { prev ->
            thumbnailCacheBytes = (thumbnailCacheBytes - bitmapByteSize(prev)).coerceAtLeast(0L)
        }
        thumbnails[tabId] = bitmap
        thumbnailCacheBytes += addedBytes
        if (thumbnailCacheBytes <= maxThumbnailCacheBytes) return emptySet()

        val evictedKeys = mutableSetOf<String>()
        while (thumbnailCacheBytes > maxThumbnailCacheBytes && thumbnails.size > 1) {
            val eldestKey = thumbnails.entries.firstOrNull { it.key != currentActiveTabId && it.key != tabId }?.key
                ?: thumbnails.entries.firstOrNull { it.key != tabId }?.key
                ?: break
            val removed = thumbnails.remove(eldestKey) ?: break
            thumbnailCacheBytes = (thumbnailCacheBytes - bitmapByteSize(removed)).coerceAtLeast(0L)
            evictedKeys += eldestKey
        }
        return evictedKeys
    }

    private fun removeThumbnailLocked(tabId: String): Boolean {
        val removed = thumbnails.remove(tabId) ?: return false
        thumbnailCacheBytes = (thumbnailCacheBytes - bitmapByteSize(removed)).coerceAtLeast(0L)
        return true
    }

    private fun bitmapByteSize(bitmap: Bitmap): Long =
        runCatching { bitmap.allocationByteCount.toLong() }.getOrDefault(0L).coerceAtLeast(1024L)

    fun setActiveTab(tabId: String?) {
        currentActiveTabId = tabId
        val snapshot = synchronized(lock) { warmSessions.entries.toList() }
        snapshot.forEach { (id, session) ->
            if (id == tabId) {
                session.resumeIfActive()
            } else {
                session.pauseIfBackground()
            }
        }
    }

    fun markAllBackgroundWebDarkDirty(activeTabId: String?) {
        val snapshot = synchronized(lock) { warmSessions.entries.toList() }
        snapshot.forEach { (id, session) ->
            if (id != activeTabId) {
                session.webDarkDirty = true
            }
        }
    }

    fun hibernateBackgroundSessions(
        keepTabId: String? = currentActiveTabId,
        evictThumbnails: Boolean = true
    ) {
        val evicted = mutableListOf<BrowserSession>()
        val evictedThumbKeys = mutableSetOf<String>()
        synchronized(lock) {
            val keysToEvict = warmSessions.entries
                .filter { (id, session) ->
                    id != keepTabId && !session.webView.isMediaCurrentlyPlaying
                }
                .map { it.key }
            for (key in keysToEvict) {
                val session = warmSessions.remove(key) ?: continue
                val bundle = Bundle()
                session.saveState(bundle)
                storeHibernatedBundleLocked(key, bundle)
                evicted += session
            }
            if (evictThumbnails) {
                val thumbKeysToClear = thumbnails.keys.filter { it != keepTabId }
                thumbKeysToClear.forEach {
                    if (removeThumbnailLocked(it)) {
                        evictedThumbKeys += it
                    }
                }
            }
        }
        evicted.forEach { session ->
            (session.webView.parent as? ViewGroup)?.removeView(session.webView)
            session.destroy()
        }
        if (evictedThumbKeys.isNotEmpty()) {
            onThumbnailsEvicted?.invoke(evictedThumbKeys)
        }
    }

    fun evictCrashedSession(tabId: String) {
        val session = synchronized(lock) {
            hibernatedBundles.remove(tabId)
            warmSessions.remove(tabId)
        }
        if (session != null) {
            (session.webView.parent as? ViewGroup)?.removeView(session.webView)
            session.destroy()
        }
    }

    fun rebindActivityContext(context: Context) {
        val snapshot = synchronized(lock) {
            currentHostContext = context
            warmSessions.values.toList()
        }
        snapshot.forEach { it.rebindContext(context) }
    }

    fun rebindAllCallbacks(callbacks: SessionHostCallbacks) {
        val snapshot = synchronized(lock) { warmSessions.values.toList() }
        snapshot.forEach { it.rebindCallbacks(callbacks) }
    }

    fun detachCallbacksOnCleared(destroyPrivateSessions: Boolean = false) {
        val privateEvicted = mutableListOf<BrowserSession>()
        val nonPrivateSnapshot: List<BrowserSession>
        synchronized(lock) {
            if (destroyPrivateSessions) {
                val privIds = warmSessions.entries.filter { it.value.isPrivate }.map { it.key }
                privIds.forEach { id ->
                    hibernatedBundles.remove(id)
                    removeThumbnailLocked(id)
                    warmSessions.remove(id)?.let { privateEvicted += it }
                }
            }
            nonPrivateSnapshot = warmSessions.values.toList()
        }
        nonPrivateSnapshot.forEach { it.rebindCallbacks(NoOpSessionHostCallbacks) }
        if (privateEvicted.isNotEmpty()) {
            privateEvicted.forEach { session ->
                (session.webView.parent as? ViewGroup)?.removeView(session.webView)
                session.destroy()
            }
            clearPrivateProfileData()
        }
    }

    fun unbindHostActivity(activityContext: Context, fallbackApplicationContext: Context) {
        val snapshot = synchronized(lock) {
            if (currentHostContext !== activityContext) {
                return
            }
            currentHostContext = fallbackApplicationContext
            warmSessions.values.toList()
        }
        snapshot.forEach { it.rebindContext(fallbackApplicationContext) }
    }

    fun getOrCreate(
        tabId: String,
        isPrivate: Boolean,
        spaceContainerProfile: String?,
        context: Context,
        callbacks: SessionHostCallbacks,
        beforeRestore: (BrowserSession) -> Unit = {}
    ): BrowserSession {
        val profileName = when {
            isPrivate -> BrowserSession.PRIVATE_PROFILE_NAME
            !spaceContainerProfile.isNullOrBlank() -> spaceContainerProfile
            else -> BrowserSession.DEFAULT_PROFILE_NAME
        }

        var hostCtx: Context = context
        var staleProfileSession: BrowserSession? = null
        val existing = synchronized(lock) {
            hostCtx = currentHostContext ?: context
            val current = warmSessions[tabId]
            if (current != null && current.profileName != profileName) {
                warmSessions.remove(tabId)
                hibernatedBundles.remove(tabId)
                staleProfileSession = current
                null
            } else {
                current
            }
        }
        staleProfileSession?.let { stale ->
            (stale.webView.parent as? ViewGroup)?.removeView(stale.webView)
            stale.destroy()
        }
        if (existing != null) {
            existing.rebindCallbacks(callbacks)
            existing.rebindContext(hostCtx)
            return existing
        }

        val created = BrowserSession(
            tabId = tabId,
            isPrivate = isPrivate,
            profileName = profileName,
            context = hostCtx,
            hostCallbacks = callbacks
        )

        var duplicateCreated = false
        val winner: BrowserSession
        var raceEvicted: BrowserSession? = null
        val lruEvicted = mutableListOf<Pair<String, BrowserSession>>()
        var savedBundle: Bundle? = null
        synchronized(lock) {
            val raceWinner = warmSessions[tabId]
            if (raceWinner != null && raceWinner.profileName == profileName) {
                duplicateCreated = true
                winner = raceWinner
            } else {
                if (raceWinner != null) {
                    warmSessions.remove(tabId)
                    raceEvicted = raceWinner
                }
                savedBundle = hibernatedBundles.remove(tabId)
                warmSessions[tabId] = created
                evictIfNeededLocked(activeTabId = tabId, outEvicted = lruEvicted)
                winner = created
            }
        }
        if (duplicateCreated) {
            created.destroy()
            winner.rebindCallbacks(callbacks)
            winner.rebindContext(hostCtx)
        } else {
            // Apply posture UA, site overrides, JS/cookie policies, and surface background
            // BEFORE restoreState() triggers Chromium's navigation load.
            beforeRestore(created)
            savedBundle?.let { saved ->
                created.restoreState(saved)
            }
        }
        raceEvicted?.let { session ->
            (session.webView.parent as? ViewGroup)?.removeView(session.webView)
            session.destroy()
        }
        if (lruEvicted.isNotEmpty()) {
            val evictedThumbKeys = mutableSetOf<String>()
            val capturedTabIds = mutableListOf<String>()
            for ((evictedId, session) in lruEvicted) {
                val capturedThumb = session.captureThumbnail()
                val bundle = Bundle()
                session.saveState(bundle)
                synchronized(lock) {
                    // Only commit hibernated state/thumbnail if the tab wasn't removed or re-warmed
                    if (!warmSessions.containsKey(evictedId)) {
                        storeHibernatedBundleLocked(evictedId, bundle)
                        if (capturedThumb != null) {
                            evictedThumbKeys += putThumbnailLocked(evictedId, capturedThumb)
                            capturedTabIds += evictedId
                        }
                    }
                }
                (session.webView.parent as? ViewGroup)?.removeView(session.webView)
                session.destroy()
            }
            if (evictedThumbKeys.isNotEmpty()) {
                onThumbnailsEvicted?.invoke(evictedThumbKeys)
            }
            capturedTabIds.forEach { id ->
                onThumbnailCaptured?.invoke(id)
            }
        }
        return winner
    }

    fun getExisting(tabId: String): BrowserSession? = synchronized(lock) {
        warmSessions[tabId]
    }

    fun clearAllDocumentStartScripts() {
        val snapshot = synchronized(lock) { warmSessions.values.toList() }
        snapshot.forEach { it.clearDocumentStartScripts() }
    }

    fun updateAllGlobalPrepaintDarkScripts(
        normalScript: String,
        privateScript: String,
        normalSubframeDarkEnabled: Boolean = false,
        privateSubframeDarkEnabled: Boolean = false,
        darkReaderInjector: DarkReaderInjector? = null
    ) {
        val snapshot = synchronized(lock) { warmSessions.values.toList() }
        snapshot.forEach { session ->
            if (darkReaderInjector != null) {
                val subframeEnabled = if (session.isPrivate) privateSubframeDarkEnabled else normalSubframeDarkEnabled
                session.updateSubframeDarkReaderRuntime(subframeEnabled, darkReaderInjector)
            }
            session.updateGlobalPrepaintDarkScript(if (session.isPrivate) privateScript else normalScript)
        }
    }

    fun discardHibernatedState(tabId: String) {
        synchronized(lock) {
            hibernatedBundles.remove(tabId)
        }
    }

    fun captureAndStoreThumbnail(tabId: String): Boolean {
        val session = synchronized(lock) { warmSessions[tabId] } ?: return false
        val bmp = session.captureThumbnail() ?: return false
        val evictedKeys = synchronized(lock) {
            putThumbnailLocked(tabId, bmp)
        }
        if (evictedKeys.isNotEmpty()) {
            onThumbnailsEvicted?.invoke(evictedKeys)
        }
        onThumbnailCaptured?.invoke(tabId)
        return true
    }

    fun getThumbnail(tabId: String): Bitmap? = synchronized(lock) {
        thumbnails[tabId]
    }

    fun remove(tabId: String) {
        val session = synchronized(lock) {
            hibernatedBundles.remove(tabId)
            removeThumbnailLocked(tabId)
            warmSessions.remove(tabId)
        }
        if (session != null) {
            (session.webView.parent as? ViewGroup)?.removeView(session.webView)
            session.destroy()
        }
    }

    fun clearPrivateProfileData() {
        deleteContainerProfile(BrowserSession.PRIVATE_PROFILE_NAME)
    }

    /**
     * Flushes persistent WebView cookies to disk for the default profile and any active
     * non-private Space container profiles so fresh sign-in / 2FA trust cookies survive
     * app backgrounding or process eviction. Must be called on the Main thread.
     */
    fun flushPersistentCookies() {
        runCatching { CookieManager.getInstance().flush() }
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) return
        val activeContainerProfiles = synchronized(lock) {
            warmSessions.values
                .asSequence()
                .filter { !it.isPrivate && it.profileName != BrowserSession.DEFAULT_PROFILE_NAME }
                .map { it.profileName }
                .distinct()
                .toList()
        }
        if (activeContainerProfiles.isEmpty()) return
        val store = runCatching { ProfileStore.getInstance() }.getOrNull() ?: return
        activeContainerProfiles.forEach { profileName ->
            runCatching { store.getProfile(profileName)?.cookieManager?.flush() }
        }
    }

    fun deleteContainerProfile(profileName: String) {
        if (profileName.isBlank() || profileName == BrowserSession.DEFAULT_PROFILE_NAME) return
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) return
        val hasWarm = synchronized(lock) { warmSessions.values.any { it.profileName == profileName } }
        if (hasWarm) return
        runCatching {
            val store = ProfileStore.getInstance()
            val profile = store.getProfile(profileName) ?: return
            profile.webStorage.deleteAllData()
            profile.cookieManager.removeAllCookies {
                runCatching {
                    profile.cookieManager.flush()
                    val stillNoWarm = synchronized(lock) {
                        warmSessions.values.none { it.profileName == profileName }
                    }
                    if (stillNoWarm) {
                        store.deleteProfile(profileName)
                    }
                }
            }
        }
    }

    fun clearProfilesData(
        containerProfiles: Collection<String>,
        clearHistory: Boolean,
        clearCookiesAndSiteData: Boolean,
        clearCache: Boolean
    ) {
        val warmSnapshot = synchronized(lock) {
            if (clearHistory) {
                hibernatedBundles.clear()
            }
            if (clearCache) {
                thumbnails.clear()
                thumbnailCacheBytes = 0L
            }
            warmSessions.values.toList()
        }
        if (clearCookiesAndSiteData) {
            runCatching {
                val defaultCm = CookieManager.getInstance()
                defaultCm.removeAllCookies {
                    runCatching { defaultCm.flush() }
                }
                WebStorage.getInstance().deleteAllData()
            }
            if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                val store = runCatching { ProfileStore.getInstance() }.getOrNull()
                if (store != null) {
                    val allNames = (store.allProfileNames + containerProfiles)
                        .filter { it.isNotBlank() && it != BrowserSession.DEFAULT_PROFILE_NAME }
                        .distinct()
                    allNames.forEach { name ->
                        runCatching {
                            val profile = store.getProfile(name) ?: return@runCatching
                            profile.webStorage.deleteAllData()
                            profile.cookieManager.removeAllCookies {
                                runCatching { profile.cookieManager.flush() }
                            }
                        }
                    }
                }
            }
        }
        warmSnapshot.forEach { session ->
            runCatching {
                if (clearCache) {
                    session.webView.clearCache(true)
                }
                if (clearCookiesAndSiteData) {
                    session.webView.clearFormData()
                }
                if (clearHistory) {
                    session.webView.clearHistory()
                }
            }
        }
    }

    private fun evictIfNeededLocked(
        activeTabId: String,
        outEvicted: MutableList<Pair<String, BrowserSession>>
    ) {
        while (warmSessions.size > maxWarmSessions) {
            // Prefer evicting non-active tabs that are not currently playing background media
            val eldestKey = warmSessions.entries.firstOrNull { (id, session) ->
                id != activeTabId && !session.webView.isMediaCurrentlyPlaying
            }?.key ?: warmSessions.keys.firstOrNull { it != activeTabId } ?: break

            val session = warmSessions.remove(eldestKey) ?: break
            outEvicted += eldestKey to session
        }
    }

    companion object {
        private const val DEFAULT_MAX_THUMBNAIL_BYTES = 8L * 1024L * 1024L
        private const val DEFAULT_MAX_HIBERNATED_BUNDLES = 40
    }
}
