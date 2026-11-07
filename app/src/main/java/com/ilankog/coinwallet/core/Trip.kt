package com.ilankog.coinwallet.core

import kotlinx.serialization.Serializable

@Serializable
data class Trip(
    val id: String,
    val name: String,
    val currency: String,
    val counts: Map<String, Int> = emptyMap(),
    val createdAt: Long,
    /** Change the user received but has not counted in yet (spec v1.1 §4). Only Done removes one. */
    val changeDue: List<ChangeNote> = emptyList(),
)

@Serializable
data class ChangeNote(val id: String, val amountMinor: Long, val at: Long)

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

    /** Currency may change only when nothing is counted and no change reminder (in that currency) is pending. */
    fun canChangeCurrency(trip: Trip): Boolean = !hasCounts(trip) && trip.changeDue.isEmpty()

    fun dismissNote(trip: Trip, id: String): Trip {
        val left = trip.changeDue.filterNot { it.id == id }
        return if (left.size == trip.changeDue.size) trip else trip.copy(changeDue = left)
    }

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
