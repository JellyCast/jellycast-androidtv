package nl.jellycast.tv.feature.home

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nl.jellycast.core.common.HomeSectionLeaves
import nl.jellycast.core.model.HomeSection

data class HomeUiState(val section: HomeSection = HomeSection.Home)

class HomeViewModel(private val sectionLeaves: HomeSectionLeaves) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun onSelectSection(section: HomeSection) {
        val left = _uiState.value.section
        if (section == left) return
        _uiState.value = HomeUiState(section)
        sectionLeaves.onLeft(left)
    }
}
