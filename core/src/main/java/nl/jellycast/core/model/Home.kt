package nl.jellycast.core.model

// Declaration order is the order in the section bar.
enum class HomeSection { Home, Series, Movies, Library }

enum class MovieView { Detail, Bookcase }

enum class LibraryKind { Movies, Series }

enum class LibrarySort { Title, Added, Year, Rating }

data class UiPreferences(
    val movieView: MovieView = MovieView.Detail,
    val libraryKind: LibraryKind = LibraryKind.Movies,
    val librarySort: LibrarySort = LibrarySort.Title,
)
