package nl.jellycast.tv.di

import android.content.Context
import androidx.datastore.core.DataStore
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import nl.jellycast.core.model.StreamChoice
import org.jellyfin.sdk.JellyfinOptions
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.verify

@OptIn(KoinExperimentalAPI::class)
class KoinModulesTest {
    @Test
    fun `all modules are complete`() {
        // Types created outside Koin on purpose (inside a lambda or by Android itself).
        module { includes(jellyCastModules) }.verify(
            extraTypes =
                listOf(
                    Context::class,
                    CoroutineDispatcher::class,
                    DataStore::class,
                    JellyfinOptions::class,
                    JellyfinOptions.Builder::class,
                    Boolean::class,
                    CoroutineScope::class,
                    File::class,
                    StreamChoice::class,
                ),
        )
    }
}
