package nl.jellycast.mobile.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import nl.jellycast.mobile.ui.info.InfoSubject

/** Whether the Chat tab shows: an API key is stored for the chosen provider. STUB: false. */
@Composable
fun isChatAvailable(): Boolean = false

/**
 * The AI chat about [subject] (one conversation per movie or series), with its input bar.
 *
 * Owner: the settings/chat worker. STUB.
 */
@Composable
fun ChatTab(subject: InfoSubject, modifier: Modifier = Modifier) = Unit
