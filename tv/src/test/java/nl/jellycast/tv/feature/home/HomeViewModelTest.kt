package nl.jellycast.tv.feature.home

import nl.jellycast.core.common.HomeSectionLeaves
import nl.jellycast.core.model.HomeSection
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeViewModelTest {
    @Test
    fun `always opens on Home`() {
        assertEquals(
            HomeUiState(HomeSection.Home),
            HomeViewModel(HomeSectionLeaves()).uiState.value,
        )
    }

    @Test
    fun `selecting a section switches immediately`() {
        val viewModel = HomeViewModel(HomeSectionLeaves())

        viewModel.onSelectSection(HomeSection.Library)

        assertEquals(HomeSection.Library, viewModel.uiState.value.section)
    }
}
