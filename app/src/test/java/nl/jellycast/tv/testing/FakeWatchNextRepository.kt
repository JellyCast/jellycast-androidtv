package nl.jellycast.tv.testing

import nl.jellycast.tv.core.data.repository.WatchNextRepository
import nl.jellycast.tv.core.model.WatchNextEntry

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
