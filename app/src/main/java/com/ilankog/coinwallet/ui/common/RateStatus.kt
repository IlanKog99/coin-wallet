package com.ilankog.coinwallet.ui.common

import com.ilankog.coinwallet.core.RateSnapshot
import com.ilankog.coinwallet.core.RelativeTime
import com.ilankog.coinwallet.data.RefreshResult

sealed interface RateStatus {
    data object Updating : RateStatus
    data object None : RateStatus
    data class Fresh(val fetchedAt: Long) : RateStatus
    data class Offline(val fetchedAt: Long) : RateStatus
}

fun rateStatus(snapshot: RateSnapshot?, busy: Boolean, last: RefreshResult?): RateStatus = when {
    busy -> RateStatus.Updating
    snapshot == null -> RateStatus.None
    last != null && last != RefreshResult.Success -> RateStatus.Offline(snapshot.fetchedAt)
    else -> RateStatus.Fresh(snapshot.fetchedAt)
}

fun RateStatus.label(now: Long): String = when (this) {
    RateStatus.Updating -> "Updating rates…"
    RateStatus.None -> "No rates yet"
    is RateStatus.Fresh -> "Rates updated ${RelativeTime.ago(fetchedAt, now)}"
    is RateStatus.Offline -> "Offline · rates from ${RelativeTime.ago(fetchedAt, now)}"
}
