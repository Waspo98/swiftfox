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
    private val maxWarmSessions: Int = 8
) {
    private val lock = Any()
    private val warmSessions = LinkedHashMap<String, BrowserSession>(maxWarmSessions, 0.75f, true)
    private val hibernatedBundles = mutableMapOf<String, Bundle>()
    private val thumbnails = mutableMapOf<String, Bitmap>()
    private var currentHostContext: Context? = null

    fun rebindActivityContext(context: Context) {
        val snapshot = synchronized(lock) {
            currentHostContext = context
            warmSessions.values.toList()
        }
        snapshot.forEach { it.rebindContext(context) }
    }

    fun getOrCreate(
        tabId: String,
        isPrivate: Boolean,
        spaceContainerProfile: String?,
        context: Context,
        callbacks: SessionHostCallbacks
    ): BrowserSession {
        var hostCtx: Context = context
        val existing = synchronized(lock) {
            hostCtx = currentHostContext ?: context
            warmSessions[tabId]
        }
        if (existing != null) {
            existing.rebindContext(hostCtx)
            return existing
        }

        val profileName = when {
            isPrivate -> BrowserSession.PRIVATE_PROFILE_NAME
            !spaceContainerProfile.isNullOrBlank() -> spaceContainerProfile
            else -> BrowserSession.DEFAULT_PROFILE_NAME
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
        synchronized(lock) {
            val raceWinner = warmSessions[tabId]
            if (raceWinner != null) {
                duplicateCreated = true
                winner = raceWinner
            } else {
                hibernatedBundles.remove(tabId)?.let { saved ->
                    created.restoreState(saved)
                }
                warmSessions[tabId] = created
                evictIfNeededLocked(activeTabId = tabId, outEvicted = evicted)
                winner = created
            }
        }
        if (duplicateCreated) {
            created.destroy()
            winner.rebindContext(hostCtx)
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
        val warmSnapshot = synchronized(lock) {
            hibernatedBundles.clear()
            warmSessions.values.toList()
        }
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
        warmSnapshot.forEach { session ->
            runCatching {
                session.webView.clearCache(true)
                session.webView.clearFormData()
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
