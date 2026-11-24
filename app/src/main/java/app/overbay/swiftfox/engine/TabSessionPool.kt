package app.overbay.swiftfox.engine

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewFeature

class TabSessionPool(
    private val maxWarmSessions: Int = 5
) {
    private val lock = Any()
    private val warmSessions = LinkedHashMap<String, BrowserSession>(maxWarmSessions, 0.75f, true)
    private val hibernatedBundles = mutableMapOf<String, Bundle>()
    private val thumbnails = mutableMapOf<String, Bitmap>()
    private var currentHostContext: Context? = null
    @Volatile
    private var currentActiveTabId: String? = null
    @Volatile
    var onThumbnailsEvicted: ((Set<String>) -> Unit)? = null

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
                hibernatedBundles[key] = bundle
                evicted += session
            }
            if (evictThumbnails) {
                val thumbKeysToClear = thumbnails.keys.filter { it != keepTabId }
                thumbKeysToClear.forEach {
                    thumbnails.remove(it)
                    evictedThumbKeys += it
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
        val evicted = mutableListOf<BrowserSession>()
        var savedBundle: Bundle? = null
        synchronized(lock) {
            val raceWinner = warmSessions[tabId]
            if (raceWinner != null && raceWinner.profileName == profileName) {
                duplicateCreated = true
                winner = raceWinner
            } else {
                if (raceWinner != null) {
                    warmSessions.remove(tabId)
                    evicted += raceWinner
                }
                savedBundle = hibernatedBundles.remove(tabId)
                warmSessions[tabId] = created
                evictIfNeededLocked(activeTabId = tabId, outEvicted = evicted)
                winner = created
            }
        }
        if (duplicateCreated) {
            created.destroy()
            winner.rebindContext(hostCtx)
        } else {
            // Apply posture UA, site overrides, JS/cookie policies, and surface background
            // BEFORE restoreState() triggers Chromium's navigation load.
            beforeRestore(created)
            savedBundle?.let { saved ->
                created.restoreState(saved)
            }
        }
        evicted.forEach { session ->
            (session.webView.parent as? ViewGroup)?.removeView(session.webView)
            session.destroy()
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

    fun updateAllGlobalPrepaintDarkScripts(normalScript: String, privateScript: String) {
        val snapshot = synchronized(lock) { warmSessions.values.toList() }
        snapshot.forEach { session ->
            session.updateGlobalPrepaintDarkScript(if (session.isPrivate) privateScript else normalScript)
        }
    }

    fun discardHibernatedState(tabId: String) {
        synchronized(lock) {
            hibernatedBundles.remove(tabId)
        }
    }

    fun captureAndStoreThumbnail(tabId: String) {
        val session = synchronized(lock) { warmSessions[tabId] } ?: return
        session.captureThumbnail()?.let { bmp ->
            synchronized(lock) {
                thumbnails[tabId] = bmp
            }
        }
    }

    fun getThumbnail(tabId: String): Bitmap? = synchronized(lock) {
        thumbnails[tabId]
    }

    fun remove(tabId: String) {
        val session = synchronized(lock) {
            hibernatedBundles.remove(tabId)
            thumbnails.remove(tabId)
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

    fun clearAllProfilesData(containerProfiles: Collection<String>) {
        clearProfilesData(
            containerProfiles = containerProfiles,
            clearHistory = true,
            clearCookiesAndSiteData = true,
            clearCache = true
        )
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

    private fun evictIfNeededLocked(activeTabId: String, outEvicted: MutableList<BrowserSession>) {
        while (warmSessions.size > maxWarmSessions) {
            // Prefer evicting non-active tabs that are not currently playing background media
            val eldestKey = warmSessions.entries.firstOrNull { (id, session) ->
                id != activeTabId && !session.webView.isMediaCurrentlyPlaying
            }?.key ?: warmSessions.keys.firstOrNull { it != activeTabId } ?: break

            val session = warmSessions.remove(eldestKey) ?: break
            session.captureThumbnail()?.let { thumbnails[eldestKey] = it }
            val bundle = Bundle()
            session.saveState(bundle)
            hibernatedBundles[eldestKey] = bundle
            outEvicted += session
        }
    }
}
