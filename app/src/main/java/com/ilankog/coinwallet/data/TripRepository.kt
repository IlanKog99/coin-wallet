package com.ilankog.coinwallet.data

import com.ilankog.coinwallet.core.DecodeResult
import com.ilankog.coinwallet.core.Trip
import com.ilankog.coinwallet.core.TripsCodec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.logging.Level
import java.util.logging.Logger
import java.util.UUID

sealed interface RepoNotice {
    data object TripsReset : RepoNotice
    data object SaveFailed : RepoNotice
    data class Deleted(val trip: Trip) : RepoNotice
}

/**
 * In-memory list is the source of truth; every change is written to trips.json.
 * All mutations go through one Mutex so rapid taps apply in order.
 */
class TripRepository(
    dir: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val file = File(dir, FILE)
    private val corruptFile = File(dir, CORRUPT_FILE)
    private val mutex = Mutex()
    private var loaded = false

    private val _trips = MutableStateFlow<List<Trip>>(emptyList())
    val trips: StateFlow<List<Trip>> = _trips.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    private val _notice = MutableStateFlow<RepoNotice?>(null)
    val notice: StateFlow<RepoNotice?> = _notice.asStateFlow()

    /** Clears [n] only if it is still the current notice. */
    fun clearNotice(n: RepoNotice) {
        _notice.compareAndSet(n, null)
    }

    suspend fun load() = mutex.withLock { ensureLoaded() }

    suspend fun create(name: String, currency: String): Trip = mutex.withLock {
        ensureLoaded()
        val trip = Trip(id = newId(), name = name, currency = currency, createdAt = clock())
        commit(_trips.value + trip)
        trip
    }

    suspend fun update(id: String, transform: (Trip) -> Trip): Trip? = mutex.withLock {
        ensureLoaded()
        val current = _trips.value.firstOrNull { it.id == id } ?: return@withLock null
        val next = transform(current)
        if (next != current) commit(_trips.value.map { if (it.id == id) next else it })
        next
    }

    suspend fun delete(id: String): Trip? = mutex.withLock {
        ensureLoaded()
        val trip = _trips.value.firstOrNull { it.id == id } ?: return@withLock null
        if (commit(_trips.value.filterNot { it.id == id })) _notice.value = RepoNotice.Deleted(trip)
        trip
    }

    suspend fun restore(trip: Trip) = mutex.withLock {
        ensureLoaded()
        if (_trips.value.none { it.id == trip.id }) commit(_trips.value + trip)
    }

    private suspend fun ensureLoaded() {
        if (loaded) return
        _trips.value = withContext(io) { readFile() }
        loaded = true // only after the read completed: a cancelled first load must retry
        _isLoaded.value = true
    }

    private fun readFile(): List<Trip> {
        val text = try {
            AtomicFiles.readOrNull(file)
        } catch (e: IOException) {
            LOG.log(Level.SEVERE, "trips read failed", e)
            return resetCorrupt("read failed: ${e.message}")
        }
        if (text == null) return emptyList()
        return when (val r = TripsCodec.decode(text)) {
            is DecodeResult.Ok -> {
                if (r.droppedKeys > 0) LOG.log(Level.WARNING, "dropped ${r.droppedKeys} unknown count keys")
                r.trips
            }
            is DecodeResult.Corrupt -> resetCorrupt(r.reason)
        }
    }

    private fun resetCorrupt(reason: String): List<Trip> {
        LOG.log(Level.SEVERE, "trips file corrupt: $reason")
        try {
            AtomicFiles.moveAside(file, corruptFile)
        } catch (e: IOException) {
            LOG.log(Level.SEVERE, "could not move corrupt trips file aside", e)
        }
        _notice.value = RepoNotice.TripsReset
        return emptyList()
    }

    /** Returns true if the list was written to disk. NonCancellable so memory never silently outruns disk. */
    private suspend fun commit(next: List<Trip>): Boolean {
        _trips.value = next
        return try {
            withContext(io + NonCancellable) { AtomicFiles.write(file, TripsCodec.encode(next)) }
            true
        } catch (e: IOException) {
            LOG.log(Level.SEVERE, "trips save failed", e)
            _notice.value = RepoNotice.SaveFailed
            false
        }
    }

    companion object {
        const val FILE = "trips.json"
        const val CORRUPT_FILE = "trips.corrupt.json"
        // java.util.logging: android.util.Log cannot run in plain JVM tests (Paparazzi classpath).
        private val LOG: Logger = Logger.getLogger("TripRepository")
    }
}
