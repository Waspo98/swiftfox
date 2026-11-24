package app.overbay.swiftfox.ui.browser

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Serializes persistent SiteOverride database mutations while suppressing stale Room emissions
 * until all queued writes settle, without overwriting newer optimistic updates or suspending in finally.
 */
internal class SiteOverrideWriteCoordinator<T> {
    private val writeMutex = Mutex()
    private val pendingWrites = AtomicInteger(0)
    private val writeGeneration = AtomicLong(0L)

    val hasPendingWrites: Boolean
        get() = pendingWrites.get() > 0

    fun beginWrite(): Long {
        val gen = writeGeneration.incrementAndGet()
        pendingWrites.incrementAndGet()
        return gen
    }

    suspend fun runSerializedWrite(
        generation: Long,
        writeToDb: suspend () -> Unit,
        readSettledFromDb: suspend () -> T,
        onSettled: (T) -> Unit
    ) {
        var settledSnapshot: T? = null
        try {
            writeMutex.withLock {
                writeToDb()
                if (writeGeneration.get() == generation) {
                    settledSnapshot = readSettledFromDb()
                }
            }
        } finally {
            if (pendingWrites.decrementAndGet() == 0) {
                val snapshot = settledSnapshot
                if (snapshot != null && writeGeneration.get() == generation) {
                    onSettled(snapshot)
                }
            }
        }
    }
}
