package com.ilankog.coinwallet.ui.common

import com.ilankog.coinwallet.data.RefreshResult
import com.ilankog.coinwallet.data.RepoNotice

sealed interface UiMessage {
    data object Offline : UiMessage
    data class ServerError(val code: Int) : UiMessage
    data object BadData : UiMessage
}

fun RefreshResult.toMessage(): UiMessage? = when (this) {
    RefreshResult.Success -> null
    RefreshResult.Offline -> UiMessage.Offline
    is RefreshResult.HttpError -> UiMessage.ServerError(code)
    RefreshResult.BadData -> UiMessage.BadData
}

fun UiMessage.text(): String = when (this) {
    UiMessage.Offline -> "You're offline. Using saved rates."
    is UiMessage.ServerError -> "Rate server error ($code)"
    UiMessage.BadData -> "Rate data was invalid"
}

fun RefreshResult.describe(): String = toMessage()?.text() ?: "Rates updated"

fun RepoNotice.text(): String = when (this) {
    RepoNotice.TripsReset -> "Saved trips were damaged and were reset"
    RepoNotice.SaveFailed -> "Couldn't save. Changes may be lost."
    is RepoNotice.Deleted -> "Trip deleted"
}
