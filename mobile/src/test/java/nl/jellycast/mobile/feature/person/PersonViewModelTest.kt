package nl.jellycast.mobile.feature.person

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import nl.jellycast.core.testing.MainDispatcherRule
import nl.jellycast.core.testing.testMovie
import nl.jellycast.core.testing.testSeries
import nl.jellycast.mobile.testing.FakeDetailRepository
import nl.jellycast.mobile.testing.FakeLibraryStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PersonViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val store =
        FakeLibraryStore(
            movies = listOf(testMovie("a"), testMovie("b"), testMovie("c")),
            series = listOf(testSeries("s1"), testSeries("s2")),
        )

    @Test
    fun `shows the library movies first, then series, in library order`() = runTest {
        val repository =
            FakeDetailRepository(personItems = mapOf("p" to listOf("s2", "c", "elsewhere", "a")))
        val viewModel = PersonViewModel("p", store, repository)
        collect(viewModel)

        val entries = (viewModel.uiState.value as PersonUiState.Ready).entries
        assertEquals(listOf("a", "c", "s2"), entries.map { it.id })
        assertTrue(entries.last() is PersonEntry.SeriesEntry)
    }

    @Test
    fun `an error offers retry, which loads again`() = runTest {
        val repository = FakeDetailRepository(personItems = mapOf("p" to listOf("b")))
        repository.failing += "p"
        val viewModel = PersonViewModel("p", store, repository)
        collect(viewModel)
        assertTrue(viewModel.uiState.value is PersonUiState.Error)

        repository.failing.clear()
        viewModel.onRetry()

        assertEquals(
            listOf("b"),
            (viewModel.uiState.value as PersonUiState.Ready).entries.map {
                it.id
            },
        )
    }

    private fun TestScope.collect(viewModel: PersonViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
    }
}
