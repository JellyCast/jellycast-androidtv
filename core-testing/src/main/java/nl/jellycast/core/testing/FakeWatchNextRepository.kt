package nl.jellycast.core.testing

import nl.jellycast.core.data.repository.WatchNextRepository
import nl.jellycast.core.model.WatchNextEntry

class FakeWatchNextRepository : WatchNextRepository {
    var published: List<WatchNextEntry>? = null
        private set

    override suspend fun publish(entries: List<WatchNextEntry>) {
        published = entries
    }

    override suspend fun clear() {
        published = emptyList()
    }
}
