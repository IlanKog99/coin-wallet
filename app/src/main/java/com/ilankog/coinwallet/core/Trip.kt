package com.ilankog.coinwallet.core

import kotlinx.serialization.Serializable

@Serializable
data class Trip(
    val id: String,
    val name: String,
    val currency: String,
    val counts: Map<String, Int> = emptyMap(),
    val createdAt: Long,
)

object TripRules {
    const val MAX_COUNT = 9999
    const val MAX_NAME = 40

    fun cleanName(raw: String): String? = raw.trim().takeIf { it.length in 1..MAX_NAME }

    fun count(trip: Trip, d: Denomination): Int = trip.counts[d.key] ?: 0

    fun setCount(trip: Trip, d: Denomination, value: Int): Trip {
        val v = value.coerceIn(0, MAX_COUNT)
        val counts = if (v == 0) trip.counts - d.key else trip.counts + (d.key to v)
        return if (counts == trip.counts) trip else trip.copy(counts = counts)
    }

    fun increment(trip: Trip, d: Denomination): Trip = setCount(trip, d, count(trip, d) + 1)

    fun decrement(trip: Trip, d: Denomination): Trip = setCount(trip, d, count(trip, d) - 1)

    fun reset(trip: Trip): Trip = trip.copy(counts = emptyMap())

    fun hasCounts(trip: Trip): Boolean = trip.counts.values.any { it > 0 }

    fun totalMinor(trip: Trip, info: CurrencyInfo): Long =
        info.denominations.sumOf { it.value * count(trip, it) }

    /** Typed count: digits only, empty = 0, max 9999. Null when invalid. */
    fun parseCount(text: String): Int? {
        val t = text.trim()
        if (t.isEmpty()) return 0
        if (!t.all { it in '0'..'9' }) return null
        val n = t.trimStart('0')
        if (n.isEmpty()) return 0
        if (n.length > 4) return null
        return n.toInt().takeIf { it <= MAX_COUNT }
    }
}
