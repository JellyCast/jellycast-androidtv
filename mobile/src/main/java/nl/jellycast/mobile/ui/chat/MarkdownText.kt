package nl.jellycast.mobile.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import nl.jellycast.mobile.core.chat.MarkdownBlock
import nl.jellycast.mobile.core.chat.MarkdownInline
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.JellyCastText
import nl.jellycast.mobile.core.designsystem.theme.JellyCastTheme

/** A chat answer in Markdown: headings, lists, tables, quotes and code, with tappable links. */
@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val blocks = remember(markdown) { MarkdownBlock.parse(markdown) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { block -> MarkdownBlockView(block) }
    }
}

@Composable
private fun MarkdownBlockView(block: MarkdownBlock) {
    val body = JellyCastText.Subheadline.copy(color = JellyCastColors.Text)
    when (block) {
        is MarkdownBlock.Heading -> Text(
            inline(block.text),
            style = if (block.level <= 2) JellyCastText.Headline else body.copy(fontWeight = FontWeight.SemiBold),
            color = JellyCastColors.Text,
            modifier = Modifier.padding(top = 4.dp),
        )

        is MarkdownBlock.Paragraph -> Text(inline(block.text), style = body)

        is MarkdownBlock.ListItem -> Row(
            Modifier.padding(start = (block.depth * LIST_INDENT_DP).dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(block.marker, style = body, color = JellyCastColors.TextMuted)
            Text(inline(block.text), style = body, modifier = Modifier.weight(1f))
        }

        is MarkdownBlock.Quote -> Text(
            inline(block.text),
            style = body,
            color = JellyCastColors.TextMuted,
            modifier =
                Modifier
                    .drawBehind {
                        val bar = QUOTE_BAR_DP.dp.toPx()
                        drawRoundRect(
                            JellyCastColors.Rail,
                            size = Size(bar, size.height),
                            cornerRadius = CornerRadius(bar / 2),
                        )
                    }
                    .padding(start = 10.dp),
        )

        is MarkdownBlock.Code -> Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(JellyCastColors.Surface)
                .horizontalScroll(rememberScrollState()),
        ) {
            Text(
                block.code,
                style = JellyCastText.Footnote.copy(fontFamily = FontFamily.Monospace),
                color = JellyCastColors.Text,
                softWrap = false,
                modifier = Modifier.padding(10.dp),
            )
        }

        is MarkdownBlock.Table -> MarkdownTable(block.header, block.rows)

        MarkdownBlock.Rule -> Box(Modifier.fillMaxWidth().height(1.dp).background(JellyCastColors.Rail))
    }
}

private const val LIST_INDENT_DP = 14
private const val QUOTE_BAR_DP = 3
private const val WRAP_AFTER_CHARACTERS = 28
private val WrappingCellWidth = 200.dp

/** Scrolls sideways when the columns are wider than the chat. */
@Composable
private fun MarkdownTable(header: List<String>, rows: List<List<String>>) {
    Box(Modifier.horizontalScroll(rememberScrollState())) {
        val columns = header.size
        Layout(
            content = {
                TableRow(header, isHeader = true)
                rows.forEachIndexed { index, row ->
                    Box(Modifier.height(if (index == 0) 2.dp else 1.dp).background(JellyCastColors.Rail))
                    TableRow(row, isHeader = false)
                }
            },
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(JellyCastColors.Surface),
        ) { measurables, _ ->
            // Children: header cells, then per row one divider and its cells.
            val cellGroups = mutableListOf<List<Measurable>>()
            val dividers = mutableListOf<Measurable>()
            var index = 0
            cellGroups += measurables.subList(index, index + columns)
            index += columns
            while (index < measurables.size) {
                dividers += measurables[index]
                cellGroups += measurables.subList(index + 1, index + 1 + columns)
                index += columns + 1
            }
            val placeables = cellGroups.map { cells -> cells.map { it.measure(Constraints()) } }
            val widths = IntArray(columns) { column -> placeables.maxOf { it[column].width } }
            val heights = placeables.map { row -> row.maxOf { it.height } }
            val totalWidth = widths.sum()
            val dividerPlaceables = dividers.map { it.measure(Constraints.fixedWidth(totalWidth)) }
            val totalHeight = heights.sum() + dividerPlaceables.sumOf { it.height }
            layout(totalWidth, totalHeight) {
                var y = 0
                placeables.forEachIndexed { rowIndex, row ->
                    if (rowIndex > 0) {
                        val divider = dividerPlaceables[rowIndex - 1]
                        divider.place(0, y)
                        y += divider.height
                    }
                    var x = 0
                    row.forEachIndexed { column, cell ->
                        cell.place(x, y)
                        x += widths[column]
                    }
                    y += heights[rowIndex]
                }
            }
        }
    }
}

@Composable
private fun TableRow(cells: List<String>, isHeader: Boolean) {
    cells.forEach { cell ->
        val style = JellyCastText.Footnote.copy(
            fontWeight = if (isHeader) FontWeight.SemiBold else FontWeight.Normal,
            color = JellyCastColors.Text,
        )
        val wraps = cell.length > WRAP_AFTER_CHARACTERS
        Text(
            inline(cell),
            style = style,
            softWrap = wraps,
            modifier =
                Modifier
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .then(if (wraps) Modifier.width(WrappingCellWidth) else Modifier),
        )
    }
}

@Composable
private fun inline(text: String): AnnotatedString {
    val runs = remember(text) { MarkdownInline.parse(text) }
    return remember(runs) {
        buildAnnotatedString {
            runs.forEach { run ->
                val style =
                    SpanStyle(
                        fontWeight = if (run.bold) FontWeight.Bold else null,
                        fontStyle = if (run.italic) FontStyle.Italic else null,
                        fontFamily = if (run.code) FontFamily.Monospace else null,
                        background = if (run.code) JellyCastColors.Surface else Color.Unspecified,
                    )
                val url = run.url
                if (url != null) {
                    withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = JellyCastColors.Accent)))) {
                        withStyle(style) { append(run.text) }
                    }
                } else {
                    withStyle(style) { append(run.text) }
                }
            }
        }
    }
}

@Preview
@Composable
private fun MarkdownTextPreview() {
    JellyCastTheme {
        MarkdownText(
            """
            ## Who is who
            Mostly **Walter** and *Jesse*, see [IMDb](https://www.imdb.com/title/tt0903747/).

            - Walter White, a chemistry teacher
              - also Heisenberg
            - Jesse Pinkman

            | Actor | Role | Season |
            |---|---|---|
            | Bryan Cranston | Walter White | 1–5 |
            | Aaron Paul | Jesse Pinkman | 1–5 |

            > Say my name.
            """.trimIndent(),
            modifier = Modifier.padding(16.dp),
        )
    }
}
