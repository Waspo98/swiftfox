package app.overbay.swiftfox.engine

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.view.ViewGroup
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewFeature

class TabSessionPool(
    private val maxWarmSessions: Int = 8
) {
    private val warmSessions = LinkedHashMap<String, BrowserSession>(maxWarmSessions, 0.75f, true)
    private val hibernatedBundles = mutableMapOf<String, Bundle>()
    private val thumbnails = mutableMapOf<String, Bitmap>()

    fun getOrCreate(
        tabId: String,
        isPrivate: Boolean,
        spaceContainerProfile: String?,
        context: Context,
        callbacks: SessionHostCallbacks
    ): BrowserSession {
        warmSessions[tabId]?.let { return it }

        val profileName = when {
            isPrivate -> BrowserSession.PRIVATE_PROFILE_NAME
            !spaceContainerProfile.isNullOrBlank() -> spaceContainerProfile
            else -> BrowserSession.DEFAULT_PROFILE_NAME
        }

        val session = BrowserSession(
            tabId = tabId,
            isPrivate = isPrivate,
            profileName = profileName,
            context = context,
            hostCallbacks = callbacks
        )

        hibernatedBundles.remove(tabId)?.let { saved ->
            session.restoreState(saved)
        }

        warmSessions[tabId] = session
        evictIfNeeded(activeTabId = tabId)
        return session
    }

    fun getExisting(tabId: String): BrowserSession? = warmSessions[tabId]

    fun getAllWarm(): Collection<BrowserSession> = warmSessions.values

    fun captureAndStoreThumbnail(tabId: String) {
        val session = warmSessions[tabId] ?: return
        session.captureThumbnail()?.let { bmp ->
            thumbnails[tabId] = bmp
        }
    }

    fun getThumbnail(tabId: String): Bitmap? = thumbnails[tabId]

    fun remove(tabId: String) {
        val session = warmSessions.remove(tabId)
        if (session != null) {
            (session.webView.parent as? ViewGroup)?.removeView(session.webView)
            session.destroy()
        }
        hibernatedBundles.remove(tabId)
        thumbnails.remove(tabId)
    }

    fun clearPrivateProfileData() {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            runCatching {
                val store = ProfileStore.getInstance()
                val profile = store.getProfile(BrowserSession.PRIVATE_PROFILE_NAME)
                profile?.cookieManager?.removeAllCookies(null)
                profile?.cookieManager?.flush()
                profile?.webStorage?.deleteAllData()
                store.deleteProfile(BrowserSession.PRIVATE_PROFILE_NAME)
            }
        }
    }

    fun destroyAll() {
        warmSessions.values.forEach { session ->
            (session.webView.parent as? ViewGroup)?.removeView(session.webView)
            session.destroy()
        }
        warmSessions.clear()
        hibernatedBundles.clear()
        thumbnails.clear()
    }

    private fun evictIfNeeded(activeTabId: String) {
        while (warmSessions.size > maxWarmSessions) {
            val eldestKey = warmSessions.keys.firstOrNull { it != activeTabId } ?: break
            val session = warmSessions.remove(eldestKey) ?: break
            session.captureThumbnail()?.let { thumbnails[eldestKey] = it }
            val bundle = Bundle()
            session.saveState(bundle)
            hibernatedBundles[eldestKey] = bundle
            (session.webView.parent as? ViewGroup)?.removeView(session.webView)
            session.destroy()
        }
    }
}
