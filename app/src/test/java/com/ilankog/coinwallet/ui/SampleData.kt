package com.ilankog.coinwallet.ui

import com.ilankog.coinwallet.core.Currencies
import com.ilankog.coinwallet.core.ManualRate
import com.ilankog.coinwallet.core.RateSnapshot
import com.ilankog.coinwallet.core.Totals
import com.ilankog.coinwallet.core.Trip
import com.ilankog.coinwallet.ui.common.RateStatus
import com.ilankog.coinwallet.ui.trips.TripsUiState
import com.ilankog.coinwallet.ui.wallet.WalletActions
import com.ilankog.coinwallet.ui.wallet.WalletUiState
import java.math.BigDecimal

object SampleData {
    const val NOW = 1_762_430_400_000L
    val ils = Currencies.orFallback("ILS")
    val snapshot = RateSnapshot(
        fetchedAt = NOW - 2 * 3_600_000L,
        date = "2026-11-06",
        rates = mapOf("EUR" to 1.0, "CZK" to 24.35, "ILS" to 3.5717, "JPY" to 176.77, "USD" to 1.1165),
    )
    val prague = Trip("t1", "Prague weekend", "CZK", mapOf("c100" to 3, "c1000" to 2, "c5000" to 1, "n20000" to 2), createdAt = 4)
    val tokyo = Trip("t2", "Tokyo", "JPY", mapOf("c1" to 7, "c100" to 4, "c500" to 2, "n1000" to 3), createdAt = 3)
    val newYork = Trip("t3", "New York", "USD", mapOf("c25" to 6, "n2000" to 1), createdAt = 2)
    val bangkok = Trip("t4", "Bangkok", "THB", mapOf("n10000" to 1), createdAt = 1)
    val allTrips = listOf(prague, tokyo, newYork, bangkok)
    val manual = mapOf("USD" to ManualRate(BigDecimal("3.6"), "ILS"))

    fun tripsState(
        status: RateStatus = RateStatus.Fresh(snapshot.fetchedAt),
        trips: List<Trip> = allTrips,
    ) = TripsUiState(
        loaded = true,
        home = ils,
        totals = Totals.all(trips, ils, manual, snapshot),
        rateStatus = status,
        now = NOW,
    )

    fun walletState(trip: Trip = prague): WalletUiState {
        val info = Currencies.orFallback(trip.currency)
        val m = manual[trip.currency]
        return WalletUiState(
            summary = Totals.summarize(trip, info, ils, m, snapshot),
            home = ils,
            rateDate = snapshot.date,
            hasManualRate = m != null,
        )
    }

    val noActions = WalletActions({}, {}, {}, {}, {}, {}, {}, {}, {})
}
