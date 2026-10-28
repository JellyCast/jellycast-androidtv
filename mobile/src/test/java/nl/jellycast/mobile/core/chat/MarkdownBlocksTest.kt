package nl.jellycast.mobile.core.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownBlocksTest {
    @Test
    fun `blocks are headings, paragraphs, lists, quotes, code, tables and rules`() {
        val markdown =
            """
            ## Who is who
            Mostly Walter
            and Jesse.

            - Walter
              - Heisenberg
            2. Second
            > Say my name.
            ```
            let x = 1
            ```
            | Actor | Role |
            |---|:---:|
            | Bryan | Walter |
            | Aaron |
            ---
            """.trimIndent()

        assertEquals(
            listOf(
                MarkdownBlock.Heading(2, "Who is who"),
                MarkdownBlock.Paragraph("Mostly Walter\nand Jesse."),
                MarkdownBlock.ListItem("•", 0, "Walter"),
                MarkdownBlock.ListItem("•", 1, "Heisenberg"),
                MarkdownBlock.ListItem("2.", 0, "Second"),
                MarkdownBlock.Quote("Say my name."),
                MarkdownBlock.Code("let x = 1"),
                MarkdownBlock.Table(
                    header = listOf("Actor", "Role"),
                    rows = listOf(listOf("Bryan", "Walter"), listOf("Aaron", "")),
                ),
                MarkdownBlock.Rule,
            ),
            MarkdownBlock.parse(markdown),
        )
    }

    @Test
    fun `a hash without a space is text`() {
        assertEquals(listOf(MarkdownBlock.Paragraph("#1 hit")), MarkdownBlock.parse("#1 hit"))
    }

    @Test
    fun `inline markdown becomes styled runs`() {
        assertEquals(
            listOf(
                InlineRun("Mostly "),
                InlineRun("Walter", bold = true),
                InlineRun(" and "),
                InlineRun("Jesse", italic = true),
                InlineRun(", see "),
                InlineRun("IMDb", url = "https://imdb.com"),
                InlineRun(" or "),
                InlineRun("code", code = true),
                InlineRun("."),
            ),
            MarkdownInline.parse(
                "Mostly **Walter** and *Jesse*, see [IMDb](https://imdb.com) or `code`.",
            ),
        )
    }

    @Test
    fun `an unmatched marker stays as text`() {
        assertEquals(listOf(InlineRun("5 * 3 = 15")), MarkdownInline.parse("5 * 3 = 15"))
    }
}
