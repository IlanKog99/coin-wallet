package com.ilankog.coinwallet.ui.settings

import com.ilankog.coinwallet.data.RefreshResult
import com.ilankog.coinwallet.ui.SampleData
import com.ilankog.coinwallet.ui.coinPaparazzi
import com.ilankog.coinwallet.ui.snapBoth
import org.junit.Rule
import org.junit.Test

class SettingsScreenSnapshotTest {
    @get:Rule
    val paparazzi = coinPaparazzi()

    private fun snap(state: SettingsUiState) = paparazzi.snapBoth { SettingsScreen(state, {}, {}, {}) }

    private val base = SettingsUiState(SampleData.ils, SampleData.snapshot, busy = false, lastResult = RefreshResult.Success, now = SampleData.NOW, version = "1.0.0")

    @Test
    fun withRates() = snap(base)

    @Test
    fun offlineNoRates() = snap(base.copy(snapshot = null, lastResult = RefreshResult.Offline))

    @Test
    fun refreshing() = snap(base.copy(busy = true, lastResult = null))
}
