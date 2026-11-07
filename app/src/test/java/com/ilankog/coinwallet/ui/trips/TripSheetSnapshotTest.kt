package com.ilankog.coinwallet.ui.trips

import com.ilankog.coinwallet.core.Currencies
import com.ilankog.coinwallet.core.CurrencyInfo
import com.ilankog.coinwallet.ui.SheetFrame
import com.ilankog.coinwallet.ui.coinPaparazzi
import com.ilankog.coinwallet.ui.snapBoth
import org.junit.Rule
import org.junit.Test

class TripSheetSnapshotTest {
    @get:Rule
    val paparazzi = coinPaparazzi()

    private fun snap(
        title: String = "New trip",
        name: String = "",
        nameError: String? = null,
        currency: CurrencyInfo? = null,
        locked: Boolean = false,
        picking: Boolean = false,
        query: String = "",
    ) = paparazzi.snapBoth {
        SheetFrame {
            TripSheetContent(
                title, name, nameError, currency, locked, picking, query,
                onNameChange = {}, onPickCurrency = {}, onQueryChange = {}, onCurrencyPicked = {},
                onClosePicker = {}, onSave = {}, onCancel = {},
            )
        }
    }

    @Test
    fun createEmpty() = snap()

    @Test
    fun invalidName() = snap(name = "   ", nameError = tripNameError("   "))

    @Test
    fun filled() = snap(name = "Prague weekend", currency = Currencies.get("CZK"))

    @Test
    fun editLockedCurrency() = snap(title = "Edit trip", name = "Prague weekend", currency = Currencies.get("CZK"), locked = true)

    @Test
    fun currencyPicker() = snap(picking = true, query = "kr", currency = Currencies.get("SEK"))
}
