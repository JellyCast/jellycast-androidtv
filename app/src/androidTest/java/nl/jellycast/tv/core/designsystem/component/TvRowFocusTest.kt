package nl.jellycast.tv.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import nl.jellycast.tv.core.designsystem.theme.JellyCastTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvRowFocusTest {
    @get:Rule
    val rule = createComposeRule()

    private val items = mutableStateOf(List(8) { "item$it" })

    private fun setContent(pendingKey: String?) {
        rule.setContent {
            JellyCastTheme {
                val items by items
                var pending by remember { mutableStateOf(pendingKey) }
                val buttonRequester = remember { FocusRequester() }
                Column {
                    Button(
                        onClick = {},
                        modifier = Modifier.testTag("button").focusRequester(buttonRequester),
                    ) { Text("Continue") }
                    TvRow(
                        items = items,
                        key = { it },
                        pendingKey = pending,
                        onPendingFocus = { pending = null },
                        header = { Text("Season 1") },
                    ) { item, itemModifier ->
                        FocusCard(onClick = {}, modifier = itemModifier.testTag(item)) {
                            Text(item)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `returning to the row focuses the pending item`() {
        setContent(pendingKey = "item3")
        rule.waitForIdle()
        rule.onNodeWithTag("item3").assertIsFocused()
    }

    @Test
    fun `when the focused item disappears its neighbour gets focus`() {
        setContent(pendingKey = "item3")
        rule.waitForIdle()

        items.value = items.value - "item3"
        rule.waitForIdle()
        rule.onNodeWithTag("item4").assertIsFocused()

        // When the last item disappears, the previous one gets focus.
        rule.onNodeWithTag("item4").performKeyInput { repeat(3) { pressKey(Key.DirectionRight) } }
        rule.waitForIdle()
        rule.onNodeWithTag("item7").assertIsFocused()
        items.value = items.value - "item7"
        rule.waitForIdle()
        rule.onNodeWithTag("item6").assertIsFocused()
    }

    @Test
    fun `entering the row with down focuses the first item`() {
        setContent(pendingKey = null)
        rule.onNodeWithTag("button").requestFocus()
        rule.onNodeWithTag("button").performKeyInput { pressKey(Key.DirectionDown) }
        rule.waitForIdle()
        rule.onNodeWithTag("item0").assertIsFocused()
    }
}
