package com.ilankog.coinwallet.core

/** Pieces to hand over (ascending by value), what the price was, and what they add up to. */
data class PaySuggestion(val pieces: List<Pair<Denomination, Int>>, val priceMinor: Long, val paidMinor: Long) {
    val changeMinor: Long get() = paidMinor - priceMinor
}

object Payment {
    /**
     * Smallest pieces first until the price is covered, then drop any piece (largest first) whose removal
     * still covers the price. Null when the price is not positive or the wallet holds less than the price.
     */
    fun suggest(trip: Trip, info: CurrencyInfo, priceMinor: Long): PaySuggestion? {
        if (priceMinor <= 0) return null
        val held = info.denominations
            .map { it to TripRules.count(trip, it) }
            .filter { it.second > 0 }
            .sortedBy { it.first.value }
        if (held.sumOf { (d, n) -> d.value * n } < priceMinor) return null

        val taken = LinkedHashMap<Denomination, Int>()
        var paid = 0L
        for ((d, n) in held) {
            if (paid >= priceMinor) break
            val need = (priceMinor - paid + d.value - 1) / d.value
            val k = minOf(n.toLong(), need).toInt()
            taken[d] = k
            paid += k * d.value
        }
        for (d in taken.keys.sortedByDescending { it.value }) {
            val drop = minOf(taken.getValue(d).toLong(), (paid - priceMinor) / d.value).toInt()
            if (drop > 0) {
                taken[d] = taken.getValue(d) - drop
                paid -= drop * d.value
            }
        }
        return PaySuggestion(taken.filterValues { it > 0 }.toList(), priceMinor, paid)
    }

    /** Removes the pieces and records change as a reminder. Null when the trip no longer holds every piece. */
    fun apply(trip: Trip, s: PaySuggestion, noteId: String, now: Long): Trip? {
        var t = trip
        for ((d, n) in s.pieces) {
            val have = TripRules.count(t, d)
            if (have < n) return null
            t = TripRules.setCount(t, d, have - n)
        }
        return if (s.changeMinor > 0) t.copy(changeDue = t.changeDue + ChangeNote(noteId, s.changeMinor, now)) else t
    }
}
