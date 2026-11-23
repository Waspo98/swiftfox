package app.overbay.swiftfox.features.sync

import app.overbay.swiftfox.data.db.SyncedRemoteTabEntity

sealed class SyncAccountState {
    data object SignedOut : SyncAccountState()
    data object Authenticating : SyncAccountState()
    data class Connected(
        val email: String? = null,
        val displayName: String? = null
    ) : SyncAccountState()

    data class NeedsReauth(
        val email: String? = null,
        val message: String = "Session expired. Please sign in again."
    ) : SyncAccountState()
}

/**
 * Permanent auth/configuration failure that periodic background workers should not retry.
 */
class SyncAuthRequiredException(message: String) : IllegalStateException(message)

data class SyncStatusSnapshot(
    val accountState: SyncAccountState = SyncAccountState.SignedOut,
    val isSyncing: Boolean = false,
    val isEngineAvailable: Boolean = true,
    val lastSyncAtMs: Long = 0L,
    val lastSyncSummary: String? = null,
    val lastSyncError: String? = null,
    val zenStatusNote: String? = null
)

data class RemoteDeviceTabsGroup(
    val deviceId: String,
    val deviceName: String,
    val deviceType: String,
    val tabs: List<SyncedRemoteTabEntity>
)
