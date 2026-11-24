package app.overbay.swiftfox

import app.overbay.swiftfox.data.db.SiteOverrideEntity
import app.overbay.swiftfox.engine.BackgroundMediaService
import app.overbay.swiftfox.ui.browser.SiteOverrideWriteCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SiteOverrideWriteCoordinatorTest {

    @Test
    fun rapidConcurrentWritesNeverClobberNewerOptimisticState() = runTest {
        val coordinator = SiteOverrideWriteCoordinator<Map<String, Boolean>>()
        val dbState = linkedMapOf<String, Boolean>()
        var uiState = mapOf<String, Boolean>()
        val settledHistory = mutableListOf<Map<String, Boolean>>()

        val firstWriteGate = CompletableDeferred<Unit>()
        val firstWriteStarted = CompletableDeferred<Unit>()

        // Write 1: optimistic UI flips example.com -> true
        uiState = uiState + ("example.com" to true)
        val gen1 = coordinator.beginWrite()
        assertTrue(coordinator.hasPendingWrites)
        val job1 = launch {
            coordinator.runSerializedWrite(
                generation = gen1,
                writeToDb = {
                    firstWriteStarted.complete(Unit)
                    firstWriteGate.await()
                    dbState["example.com"] = true
                },
                readSettledFromDb = { dbState.toMap() },
                onSettled = { snapshot ->
                    uiState = snapshot
                    settledHistory += snapshot
                }
            )
        }

        firstWriteStarted.await()

        // Write 2 arrives before Write 1 finishes: optimistic UI flips example.com -> false
        uiState = uiState + ("example.com" to false)
        val gen2 = coordinator.beginWrite()
        val job2 = launch {
            coordinator.runSerializedWrite(
                generation = gen2,
                writeToDb = { dbState["example.com"] = false },
                readSettledFromDb = { dbState.toMap() },
                onSettled = { snapshot ->
                    uiState = snapshot
                    settledHistory += snapshot
                }
            )
        }

        // Release Write 1; it must NOT overwrite uiState with true
        firstWriteGate.complete(Unit)
        job1.join()
        assertEquals(false, uiState["example.com"])
        job2.join()

        assertFalse(coordinator.hasPendingWrites)
        assertEquals(mapOf("example.com" to false), uiState)
        assertEquals(listOf(mapOf("example.com" to false)), settledHistory)
    }

    @Test
    fun newerWriteStartingDuringReadPreventsStaleSettlement() = runTest {
        val coordinator = SiteOverrideWriteCoordinator<Map<String, Boolean>>()
        val dbState = linkedMapOf<String, Boolean>()
        var uiState = mapOf("example.com" to true)
        val readEntered = CompletableDeferred<Unit>()
        val allowReadReturn = CompletableDeferred<Unit>()
        val settledHistory = mutableListOf<Map<String, Boolean>>()

        val gen1 = coordinator.beginWrite()
        val job1 = launch {
            coordinator.runSerializedWrite(
                generation = gen1,
                writeToDb = { dbState["example.com"] = true },
                readSettledFromDb = {
                    val snap = dbState.toMap()
                    readEntered.complete(Unit)
                    allowReadReturn.await()
                    snap
                },
                onSettled = { snapshot ->
                    uiState = snapshot
                    settledHistory += snapshot
                }
            )
        }

        readEntered.await()

        // Newer write starts while Write 1 is still inside readSettledFromDb
        uiState = mapOf("example.com" to false)
        val gen2 = coordinator.beginWrite()
        val job2 = launch {
            coordinator.runSerializedWrite(
                generation = gen2,
                writeToDb = { dbState["example.com"] = false },
                readSettledFromDb = { dbState.toMap() },
                onSettled = { snapshot ->
                    uiState = snapshot
                    settledHistory += snapshot
                }
            )
        }

        allowReadReturn.complete(Unit)
        job1.join()
        assertEquals(false, uiState["example.com"])
        job2.join()

        assertFalse(coordinator.hasPendingWrites)
        assertEquals(mapOf("example.com" to false), uiState)
        assertEquals(listOf(mapOf("example.com" to false)), settledHistory)
    }

    @Test
    fun cancelledWriteClearsPendingCounterCleanly() = runTest {
        val coordinator = SiteOverrideWriteCoordinator<Map<String, Boolean>>()
        val writeEntered = CompletableDeferred<Unit>()
        val neverCompletes = CompletableDeferred<Unit>()

        val gen = coordinator.beginWrite()
        assertTrue(coordinator.hasPendingWrites)
        val job = launch {
            coordinator.runSerializedWrite(
                generation = gen,
                writeToDb = {
                    writeEntered.complete(Unit)
                    neverCompletes.await()
                },
                readSettledFromDb = { emptyMap() },
                onSettled = { error("Cancelled write must not settle") }
            )
        }

        writeEntered.await()
        job.cancel(CancellationException("test cancel"))
        job.join()

        assertFalse(coordinator.hasPendingWrites)
    }

    @Test
    fun backgroundMediaToggleCommandHandlesPendingPlayConfirmation() {
        assertEquals(
            BackgroundMediaService.MediaCommand.PLAY,
            BackgroundMediaService.resolveToggleCommand(isPlaying = false, pendingPlayConfirmation = false)
        )
        assertEquals(
            BackgroundMediaService.MediaCommand.PAUSE,
            BackgroundMediaService.resolveToggleCommand(isPlaying = false, pendingPlayConfirmation = true)
        )
        assertEquals(
            BackgroundMediaService.MediaCommand.PAUSE,
            BackgroundMediaService.resolveToggleCommand(isPlaying = true, pendingPlayConfirmation = false)
        )
        assertEquals(
            BackgroundMediaService.MediaCommand.PAUSE,
            BackgroundMediaService.resolveToggleCommand(isPlaying = true, pendingPlayConfirmation = true)
        )
    }

    @Test
    fun siteOverrideIsDefaultDetectsEveryOverrideFieldAndBlankUserAgent() {
        val base = SiteOverrideEntity(host = "example.com")
        assertTrue(base.isDefault())
        assertTrue(base.copy(host = "www.example.com", customUserAgent = "").isDefault())
        assertTrue(base.copy(customUserAgent = "   ").isDefault())

        assertFalse(base.copy(siteModeOverride = "DESKTOP").isDefault())
        assertFalse(base.copy(httpsOnlyBypass = true).isDefault())
        assertFalse(base.copy(externalAppPolicyOverride = "NATIVE_APP").isDefault())
        assertFalse(base.copy(allowCamera = true).isDefault())
        assertFalse(base.copy(allowMic = false).isDefault())
        assertFalse(base.copy(allowLocation = true).isDefault())
        assertFalse(base.copy(allowNotifications = false).isDefault())
        assertFalse(base.copy(allowProtectedMedia = true).isDefault())
        assertFalse(base.copy(javascriptEnabled = false).isDefault())
        assertFalse(base.copy(webDarkModeOverride = true).isDefault())
        assertFalse(base.copy(webDarkModeOverride = false).isDefault())
        assertFalse(base.copy(adblockDisabled = true).isDefault())
        assertFalse(base.copy(cookiesEnabled = false).isDefault())
        assertFalse(base.copy(customUserAgent = "CustomUA/1.0").isDefault())
    }
}
