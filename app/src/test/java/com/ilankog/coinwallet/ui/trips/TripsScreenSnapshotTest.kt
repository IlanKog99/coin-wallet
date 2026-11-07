package com.ilankog.coinwallet.ui.trips

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import com.ilankog.coinwallet.core.ChangeNote
import com.ilankog.coinwallet.ui.SampleData
import com.ilankog.coinwallet.ui.coinPaparazzi
import com.ilankog.coinwallet.ui.common.RateStatus
import com.ilankog.coinwallet.ui.snapBoth
import org.junit.Rule
import org.junit.Test

class TripsScreenSnapshotTest {
    @get:Rule
    val paparazzi = coinPaparazzi()

    private fun snap(state: TripsUiState, selected: Set<String> = emptySet()) = paparazzi.snapBoth {
        TripsScreen(state, remember { SnackbarHostState() }, {}, {}, {}, {}, selected = selected)
    }

    @Test
    fun filled() = snap(SampleData.tripsState())

    @Test
    fun empty() = snap(SampleData.tripsState(status = RateStatus.None, trips = emptyList()))

    @Test
    fun updating() = snap(SampleData.tripsState(status = RateStatus.Updating))

    @Test
    fun offline() = snap(SampleData.tripsState(status = RateStatus.Offline(SampleData.NOW - 3 * 86_400_000L)))

    @Test
    fun selectMode() = snap(SampleData.tripsState(), selected = setOf("t1", "t3"))

    @Test
    fun reminderBadge() = snap(
        SampleData.tripsState(
            trips = listOf(
                SampleData.prague.copy(changeDue = listOf(ChangeNote("n1", 800, 0), ChangeNote("n2", 1_500, 1))),
            ) + SampleData.allTrips.drop(1),
        ),
    )
}
