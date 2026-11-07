package com.ilankog.coinwallet.core

data class TripSummary(
    val trip: Trip,
    val info: CurrencyInfo,
    val totalMinor: Long,
    val homeMinor: Long?,
    val rate: ResolvedRate?,
)

data class WalletTotals(val trips: List<TripSummary>, val grandHomeMinor: Long, val missingRateCount: Int)

object Totals {
    fun summarize(trip: Trip, info: CurrencyInfo, home: CurrencyInfo, manual: ManualRate?, snapshot: RateSnapshot?): TripSummary {
        val total = TripRules.totalMinor(trip, info)
        val rate = Conversion.resolve(info.code, home.code, manual, snapshot)
        return TripSummary(trip, info, total, rate?.let { Conversion.convert(total, info, home, it.rate) }, rate)
    }

    fun all(trips: List<Trip>, home: CurrencyInfo, manual: Map<String, ManualRate>, snapshot: RateSnapshot?): WalletTotals {
        val summaries = trips.sortedByDescending { it.createdAt }.mapNotNull { t ->
            Currencies.get(t.currency)?.let { summarize(t, it, home, manual[t.currency], snapshot) }
        }
        return WalletTotals(
            trips = summaries,
            grandHomeMinor = summaries.sumOf { it.homeMinor ?: 0L },
            missingRateCount = summaries.count { it.homeMinor == null },
        )
    }
}
