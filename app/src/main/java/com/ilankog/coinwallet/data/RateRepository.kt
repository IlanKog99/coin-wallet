package com.ilankog.coinwallet.data

import com.ilankog.coinwallet.core.ParseResult
import com.ilankog.coinwallet.core.RateSnapshot
import com.ilankog.coinwallet.core.RatesParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.logging.Level
import java.util.logging.Logger

sealed interface RefreshResult {
    data object Success : RefreshResult
    data object Offline : RefreshResult
    data class HttpError(val code: Int) : RefreshResult
    data object BadData : RefreshResult
}

/**
 * Cached ECB rates. refresh() is single-flight: the request runs in [scope] (app-wide),
 * so a caller leaving the screen never cancels it and busy is always cleared in finally.
 */
class RateRepository(
    dir: File,
    private val fetcher: HttpFetcher,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val file = File(dir, FILE)
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    private var loaded = false
    private var inFlight: Deferred<RefreshResult>? = null

    private val _snapshot = MutableStateFlow<RateSnapshot?>(null)
    val snapshot: StateFlow<RateSnapshot?> = _snapshot.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _lastResult = MutableStateFlow<RefreshResult?>(null)
    val lastResult: StateFlow<RefreshResult?> = _lastResult.asStateFlow()

    /** Reads the cache once. `loaded` is set only after the read, so a cancelled first load is retried. */
    suspend fun load(): Unit = lock.withLock {
        if (loaded) return@withLock
        val cached = withContext(io) { readCache() }
        if (_snapshot.value == null) _snapshot.value = cached
        loaded = true
    }

    suspend fun refresh(): RefreshResult {
        val job = lock.withLock {
            inFlight?.takeIf { it.isActive } ?: scope.async { doRefresh() }.also { inFlight = it }
        }
        return job.await()
    }

    private suspend fun doRefresh(): RefreshResult {
        _busy.value = true
        try {
            val result = fetchAndStore()
            _lastResult.value = result
            return result
        } finally {
            _busy.value = false
        }
    }

    private suspend fun fetchAndStore(): RefreshResult {
        val response = try {
            withContext(io) { fetcher.get(URL) }
        } catch (e: IOException) {
            LOG.log(Level.WARNING, "rates fetch failed: ${e.message}")
            return RefreshResult.Offline
        }
        if (response.code != 200) {
            LOG.log(Level.WARNING, "rates fetch HTTP ${response.code}")
            return RefreshResult.HttpError(response.code)
        }
        return when (val parsed = RatesParser.parse(response.body)) {
            is ParseResult.Error -> {
                LOG.log(Level.WARNING, "rates parse failed: ${parsed.reason}")
                RefreshResult.BadData
            }
            is ParseResult.Ok -> {
                val snap = RateSnapshot(clock(), parsed.date, parsed.rates)
                _snapshot.value = snap
                try {
                    withContext(io) { AtomicFiles.write(file, json.encodeToString(RateSnapshot.serializer(), snap)) }
                } catch (e: IOException) {
                    LOG.log(Level.WARNING, "rates cache write failed", e)
                }
                RefreshResult.Success
            }
        }
    }

    private fun readCache(): RateSnapshot? {
        val text = try {
            AtomicFiles.readOrNull(file)
        } catch (e: IOException) {
            LOG.log(Level.WARNING, "rates cache read failed", e)
            return null
        }
        if (text == null) return null
        return try {
            val snap = json.decodeFromString(RateSnapshot.serializer(), text)
            val rates = RatesParser.plausible(snap.rates)
            if (rates.size != snap.rates.size) LOG.log(Level.WARNING, "dropped ${snap.rates.size - rates.size} implausible cached rates")
            if (rates.size < 2) null else snap.copy(rates = rates)
        } catch (e: IllegalArgumentException) {
            LOG.log(Level.WARNING, "rates cache corrupt, deleting", e)
            if (!file.delete()) LOG.log(Level.WARNING, "could not delete corrupt rates cache")
            null
        }
    }

    companion object {
        const val URL = "https://api.frankfurter.dev/v1/latest"
        const val FILE = "rates.json"
        // java.util.logging: android.util.Log cannot run in plain JVM tests (Paparazzi classpath).
        private val LOG: Logger = Logger.getLogger("RateRepository")
    }
}
