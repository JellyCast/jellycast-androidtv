package nl.jellycast.mobile.core.chat

/** The block structure of a chat answer: headings, lists, tables, quotes and code. */
sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock

    data class Paragraph(val text: String) : MarkdownBlock

    data class ListItem(val marker: String, val depth: Int, val text: String) : MarkdownBlock

    data class Quote(val text: String) : MarkdownBlock

    data class Code(val code: String) : MarkdownBlock

    data class Table(val header: List<String>, val rows: List<List<String>>) : MarkdownBlock

    data object Rule : MarkdownBlock

    companion object {
        fun parse(markdown: String): List<MarkdownBlock> = MarkdownParser(markdown.lines()).blocks()
    }
}

private class MarkdownParser(private val lines: List<String>) {
    private var position = 0
    private val paragraph = mutableListOf<String>()
    private val result = mutableListOf<MarkdownBlock>()

    fun blocks(): List<MarkdownBlock> {
        while (position < lines.size) {
            val line = lines[position]
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> {
                    flushParagraph()
                    position++
                }

                trimmed.startsWith("```") -> {
                    flushParagraph()
                    readCode()
                }

                isTableStart() -> {
                    flushParagraph()
                    readTable()
                }

                else -> {
                    val block = singleLineBlock(line, trimmed)
                    if (block != null) {
                        flushParagraph()
                        result += block
                    } else {
                        paragraph += trimmed
                    }
                    position++
                }
            }
        }
        flushParagraph()
        return result
    }

    private fun flushParagraph() {
        if (paragraph.isEmpty()) return
        result += MarkdownBlock.Paragraph(paragraph.joinToString("\n"))
        paragraph.clear()
    }

    private fun singleLineBlock(line: String, trimmed: String): MarkdownBlock? = when {
        isRule(trimmed) -> MarkdownBlock.Rule
        heading(trimmed) != null -> heading(trimmed)
        trimmed.startsWith(">") -> MarkdownBlock.Quote(trimmed.drop(1).trim())
        else -> listItem(line)
    }

    private fun isRule(trimmed: String): Boolean {
        val compact = trimmed.replace(" ", "")
        val first = compact.firstOrNull() ?: return false
        return compact.length >= MIN_RULE_LENGTH && first in "-*_" && compact.all { it == first }
    }

    private fun heading(trimmed: String): MarkdownBlock.Heading? {
        val hashes = trimmed.takeWhile { it == '#' }.length
        if (hashes !in 1..MAX_HEADING_LEVEL) return null
        val rest = trimmed.drop(hashes)
        if (!rest.startsWith(" ")) return null
        return MarkdownBlock.Heading(hashes, rest.trim())
    }

    private fun listItem(line: String): MarkdownBlock.ListItem? {
        val leading = line.takeWhile { it == ' ' || it == '\t' }
        val indent = leading.sumOf { if (it == '\t') SPACES_PER_DEPTH else 1 }
        val body = line.drop(leading.length)
        val depth = indent / SPACES_PER_DEPTH
        if (body.length >= 2 && body[0] in "-*+" && body[1] == ' ') {
            return MarkdownBlock.ListItem("•", depth, body.drop(2))
        }
        val digits = body.takeWhile { it.isDigit() }
        val afterDigits = body.drop(digits.length)
        val isNumbered =
            digits.isNotEmpty() &&
                digits.length <= MAX_LIST_DIGITS &&
                afterDigits.length >= 2 &&
                afterDigits[0] in ".)" &&
                afterDigits[1] == ' '
        return if (isNumbered) MarkdownBlock.ListItem("$digits.", depth, afterDigits.drop(2)) else null
    }

    private fun readCode() {
        position++
        val code = mutableListOf<String>()
        while (position < lines.size && !lines[position].trim().startsWith("```")) {
            code += lines[position]
            position++
        }
        position++
        result += MarkdownBlock.Code(code.joinToString("\n"))
    }

    private fun isTableStart(): Boolean = position + 1 < lines.size &&
        lines[position].trim().startsWith("|") &&
        isSeparator(lines[position + 1])

    private fun isSeparator(line: String): Boolean {
        val cells = cells(line)
        return cells.isNotEmpty() &&
            cells.all { cell -> cell.isNotEmpty() && cell.all { it in "-:" } && '-' in cell }
    }

    private fun readTable() {
        val header = cells(lines[position])
        position += 2
        val rows = mutableListOf<List<String>>()
        while (position < lines.size && lines[position].trim().startsWith("|")) {
            val row = cells(lines[position])
            rows += (row + List(header.size) { "" }).take(header.size)
            position++
        }
        result += MarkdownBlock.Table(header, rows)
    }

    private fun cells(line: String): List<String> =
        line.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }

    private companion object {
        const val SPACES_PER_DEPTH = 2
        const val MAX_HEADING_LEVEL = 6
        const val MAX_LIST_DIGITS = 3
        const val MIN_RULE_LENGTH = 3
    }
}

/** A stretch of inline text with one style. */
data class InlineRun(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val url: String? = null,
)

/** Inline Markdown (bold, italic, code, links) as runs; unmatched markers stay as text. */
object MarkdownInline {
    private val Link = Regex("""\[([^\]]+)]\(([^)\s]+)\)""")

    fun parse(text: String): List<InlineRun> {
        val runs = mutableListOf<InlineRun>()
        parseInto(text, InlineRun(""), runs)
        return merge(runs)
    }

    private fun parseInto(text: String, style: InlineRun, out: MutableList<InlineRun>) {
        var index = 0
        val plain = StringBuilder()
        fun flush() {
            if (plain.isNotEmpty()) out += style.copy(text = plain.toString())
            plain.clear()
        }
        while (index < text.length) {
            val rest = text.substring(index)
            val consumed = when {
                rest.startsWith("`") -> code(rest, style, out, ::flush)
                rest.startsWith("**") || rest.startsWith("__") ->
                    styled(rest, rest.take(2), style.copy(bold = true), out, ::flush)
                rest.startsWith("*") || rest.startsWith("_") ->
                    styled(rest, rest.take(1), style.copy(italic = true), out, ::flush)
                rest.startsWith("[") -> link(rest, style, out, ::flush)
                else -> 0
            }
            if (consumed > 0) {
                index += consumed
            } else {
                plain.append(text[index])
                index++
            }
        }
        flush()
    }

    private fun code(
        rest: String,
        style: InlineRun,
        out: MutableList<InlineRun>,
        flush: () -> Unit,
    ): Int {
        val end = rest.indexOf('`', startIndex = 1)
        if (end <= 1) return 0
        flush()
        out += style.copy(text = rest.substring(1, end), code = true)
        return end + 1
    }

    private fun styled(
        rest: String,
        marker: String,
        style: InlineRun,
        out: MutableList<InlineRun>,
        flush: () -> Unit,
    ): Int {
        val end = rest.indexOf(marker, startIndex = marker.length)
        if (end <= marker.length || rest[marker.length].isWhitespace()) return 0
        flush()
        parseInto(rest.substring(marker.length, end), style, out)
        return end + marker.length
    }

    private fun link(
        rest: String,
        style: InlineRun,
        out: MutableList<InlineRun>,
        flush: () -> Unit,
    ): Int {
        val match = Link.matchAt(rest, 0) ?: return 0
        flush()
        parseInto(match.groupValues[1], style.copy(url = match.groupValues[2]), out)
        return match.value.length
    }

    private fun merge(runs: List<InlineRun>): List<InlineRun> = runs.fold(mutableListOf()) { acc, run ->
        val last = acc.lastOrNull()
        if (last != null && last.copy(text = "") == run.copy(text = "")) {
            acc[acc.lastIndex] = last.copy(text = last.text + run.text)
        } else {
            acc += run
        }
        acc
    }
}
