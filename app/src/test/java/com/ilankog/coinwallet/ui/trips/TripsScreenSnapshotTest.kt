package com.ilankog.coinwallet.ui.trips

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import com.ilankog.coinwallet.ui.SampleData
import com.ilankog.coinwallet.ui.coinPaparazzi
import com.ilankog.coinwallet.ui.common.RateStatus
import com.ilankog.coinwallet.ui.snapBoth
import org.junit.Rule
import org.junit.Test

class TripsScreenSnapshotTest {
    @get:Rule
    val paparazzi = coinPaparazzi()

    private fun snap(state: TripsUiState) = paparazzi.snapBoth {
        TripsScreen(state, remember { SnackbarHostState() }, {}, {}, {}, {})
    }

    @Test
    fun filled() = snap(SampleData.tripsState())

    @Test
    fun empty() = snap(SampleData.tripsState(status = RateStatus.None, trips = emptyList()))

    @Test
    fun updating() = snap(SampleData.tripsState(status = RateStatus.Updating))

    @Test
    fun offline() = snap(SampleData.tripsState(status = RateStatus.Offline(SampleData.NOW - 3 * 86_400_000L)))
}
