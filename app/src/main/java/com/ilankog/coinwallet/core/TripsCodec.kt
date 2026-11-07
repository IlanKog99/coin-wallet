package com.ilankog.coinwallet.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

sealed interface DecodeResult {
    data class Ok(val trips: List<Trip>, val droppedKeys: Int) : DecodeResult
    data class Corrupt(val reason: String) : DecodeResult
}

object TripsCodec {
    const val VERSION = 1

    @Serializable
    private data class FileBody(val version: Int, val trips: List<Trip>)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(trips: List<Trip>): String = json.encodeToString(FileBody.serializer(), FileBody(VERSION, trips))

    fun decode(text: String): DecodeResult {
        val body = try {
            json.decodeFromString(FileBody.serializer(), text)
        } catch (e: IllegalArgumentException) {
            return DecodeResult.Corrupt("parse: ${e.message}")
        }
        if (body.version != VERSION) return DecodeResult.Corrupt("version ${body.version}")
        var dropped = 0
        val trips = body.trips.map { t ->
            val info = Currencies.get(t.currency) ?: return DecodeResult.Corrupt("unknown currency ${t.currency}")
            if (t.counts.values.any { it !in 0..TripRules.MAX_COUNT }) return DecodeResult.Corrupt("count out of range in ${t.id}")
            val valid = info.denominations.map { it.key }.toSet()
            val kept = t.counts.filterKeys { it in valid }
            dropped += t.counts.size - kept.size
            t.copy(counts = kept)
        }
        return DecodeResult.Ok(trips, dropped)
    }
}
