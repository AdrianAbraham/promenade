package net.shadowspire.promenade2.domain.instructions

object MarkdownInstructionParser {
    fun parse(text: String): MarkdownInstructionDocument {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        val lines = normalized.split('\n')
        val blocks = mutableListOf<MarkdownBlock>()
        var index = 0

        while (index < lines.size) {
            val line = lines[index]
            when {
                line.isBlank() -> index += 1
                line.isFenceStart() -> {
                    val parsed = parseCodeBlock(lines, index)
                    blocks += parsed.block
                    index = parsed.nextIndex
                }

                line.headingText() != null -> {
                    val heading = line.headingText()
                    if (heading != null) {
                        blocks += MarkdownBlock.Heading(
                            level = heading.level,
                            content = parseInline(heading.text),
                        )
                    }
                    index += 1
                }

                line.isListItem() -> {
                    val items = mutableListOf<List<MarkdownInline>>()
                    while (index < lines.size && lines[index].isListItem()) {
                        items += parseInline(lines[index].listItemText())
                        index += 1
                    }
                    blocks += MarkdownBlock.BulletList(items)
                }

                line.trimStart().startsWith(BLOCK_QUOTE_PREFIX) -> {
                    val parts = mutableListOf<String>()
                    while (index < lines.size && lines[index].trimStart().startsWith(BLOCK_QUOTE_PREFIX)) {
                        parts += lines[index].trimStart().removePrefix(BLOCK_QUOTE_PREFIX).trimStart()
                        index += 1
                    }
                    blocks += MarkdownBlock.BlockQuote(parseInline(parts.joinToString(" ")))
                }

                else -> {
                    val paragraphLines = mutableListOf(line.trim())
                    index += 1
                    while (index < lines.size && lines[index].isParagraphContinuation()) {
                        paragraphLines += lines[index].trim()
                        index += 1
                    }
                    blocks += MarkdownBlock.Paragraph(parseInline(paragraphLines.joinToString(" ")))
                }
            }
        }

        return MarkdownInstructionDocument(blocks)
    }

    fun parseInline(text: String): List<MarkdownInline> {
        if (text.isEmpty()) {
            return emptyList()
        }

        val spans = mutableListOf<MarkdownInline>()
        val plain = StringBuilder()
        var index = 0

        fun flushPlain() {
            if (plain.isNotEmpty()) {
                spans += MarkdownInline.Text(plain.toString())
                plain.clear()
            }
        }

        while (index < text.length) {
            val parsed = parseInlineAt(text, index)
            if (parsed == null) {
                plain.append(text[index])
                index += 1
            } else {
                flushPlain()
                spans += parsed.inline
                index = parsed.nextIndex
            }
        }
        flushPlain()
        return spans
    }

    private fun parseInlineAt(
        text: String,
        index: Int,
    ): InlineParse? =
        when {
            text[index] == INLINE_CODE_MARKER -> parseDelimited(
                text = text,
                index = index,
                delimiter = INLINE_CODE_MARKER.toString(),
                factory = MarkdownInline::Code,
            )

            text.startsWith(STRONG_ASTERISK, index) -> parseDelimited(
                text = text,
                index = index,
                delimiter = STRONG_ASTERISK,
                factory = MarkdownInline::Strong,
            )

            text.startsWith(STRONG_UNDERSCORE, index) -> parseDelimited(
                text = text,
                index = index,
                delimiter = STRONG_UNDERSCORE,
                factory = MarkdownInline::Strong,
            )

            text[index] == EMPHASIS_ASTERISK -> parseDelimited(
                text = text,
                index = index,
                delimiter = EMPHASIS_ASTERISK.toString(),
                factory = MarkdownInline::Emphasis,
            )

            text[index] == EMPHASIS_UNDERSCORE -> parseDelimited(
                text = text,
                index = index,
                delimiter = EMPHASIS_UNDERSCORE.toString(),
                factory = MarkdownInline::Emphasis,
            )

            text[index] == LINK_LABEL_START -> parseLink(text, index)
            else -> null
        }

    private fun parseDelimited(
        text: String,
        index: Int,
        delimiter: String,
        factory: (String) -> MarkdownInline,
    ): InlineParse? {
        val contentStart = index + delimiter.length
        val closing = text.indexOf(delimiter, startIndex = contentStart)
        if (closing <= contentStart) {
            return null
        }
        return InlineParse(
            inline = factory(text.substring(contentStart, closing)),
            nextIndex = closing + delimiter.length,
        )
    }

    private fun parseLink(
        text: String,
        index: Int,
    ): InlineParse? {
        val labelEnd = text.indexOf(LINK_LABEL_END, startIndex = index + 1)
        if (labelEnd <= index + 1 || labelEnd + 1 >= text.length || text[labelEnd + 1] != LINK_URL_START) {
            return null
        }
        val urlEnd = text.indexOf(LINK_URL_END, startIndex = labelEnd + 2)
        if (urlEnd <= labelEnd + 2) {
            return null
        }
        return InlineParse(
            inline = MarkdownInline.Link(
                label = text.substring(index + 1, labelEnd),
                url = text.substring(labelEnd + 2, urlEnd),
            ),
            nextIndex = urlEnd + 1,
        )
    }

    private fun parseCodeBlock(
        lines: List<String>,
        startIndex: Int,
    ): BlockParse {
        val start = lines[startIndex].trimStart()
        val fence = start.takeWhile { character -> character == FENCE_BACKTICK || character == FENCE_TILDE }
        val language = start.removePrefix(fence).trim().ifBlank { null }
        val codeLines = mutableListOf<String>()
        var index = startIndex + 1
        while (index < lines.size && !lines[index].trimStart().startsWith(fence)) {
            codeLines += lines[index]
            index += 1
        }
        val nextIndex = if (index < lines.size) index + 1 else index
        return BlockParse(
            block = MarkdownBlock.CodeBlock(
                language = language,
                code = codeLines.joinToString("\n"),
            ),
            nextIndex = nextIndex,
        )
    }

    private fun String.headingText(): HeadingParse? {
        val trimmed = trimStart()
        val markerCount = trimmed.takeWhile { character -> character == HEADING_MARKER }.length
        if (markerCount !in MIN_HEADING_LEVEL..MAX_HEADING_LEVEL) {
            return null
        }
        if (trimmed.length <= markerCount || !trimmed[markerCount].isWhitespace()) {
            return null
        }
        return HeadingParse(
            level = markerCount,
            text = trimmed.drop(markerCount).trim(),
        )
    }

    private fun String.isFenceStart(): Boolean {
        val trimmed = trimStart()
        return trimmed.startsWith(FENCE_BACKTICK.toString().repeat(FENCE_LENGTH)) ||
            trimmed.startsWith(FENCE_TILDE.toString().repeat(FENCE_LENGTH))
    }

    private fun String.isListItem(): Boolean {
        val trimmed = trimStart()
        return unorderedListMarkers.any { marker -> trimmed.startsWith("$marker ") } ||
            orderedListRegex.matches(trimmed)
    }

    private fun String.listItemText(): String {
        val trimmed = trimStart()
        val unorderedMarker = unorderedListMarkers.firstOrNull { marker -> trimmed.startsWith("$marker ") }
        if (unorderedMarker != null) {
            return trimmed.drop(2).trim()
        }
        return trimmed.replaceFirst(orderedListPrefixRegex, "").trim()
    }

    private fun String.isParagraphContinuation(): Boolean =
        isNotBlank() &&
            !isFenceStart() &&
            headingText() == null &&
            !isListItem() &&
            !trimStart().startsWith(BLOCK_QUOTE_PREFIX)

    private data class BlockParse(
        val block: MarkdownBlock,
        val nextIndex: Int,
    )

    private data class HeadingParse(
        val level: Int,
        val text: String,
    )

    private data class InlineParse(
        val inline: MarkdownInline,
        val nextIndex: Int,
    )

    private val unorderedListMarkers = listOf("-", "*", "+")
    private val orderedListRegex = Regex("\\d+[.)]\\s+.+")
    private val orderedListPrefixRegex = Regex("^\\d+[.)]\\s+")

    private const val MIN_HEADING_LEVEL = 1
    private const val MAX_HEADING_LEVEL = 6
    private const val FENCE_LENGTH = 3
    private const val HEADING_MARKER = '#'
    private const val BLOCK_QUOTE_PREFIX = ">"
    private const val FENCE_BACKTICK = '`'
    private const val FENCE_TILDE = '~'
    private const val INLINE_CODE_MARKER = '`'
    private const val STRONG_ASTERISK = "**"
    private const val STRONG_UNDERSCORE = "__"
    private const val EMPHASIS_ASTERISK = '*'
    private const val EMPHASIS_UNDERSCORE = '_'
    private const val LINK_LABEL_START = '['
    private const val LINK_LABEL_END = ']'
    private const val LINK_URL_START = '('
    private const val LINK_URL_END = ')'
}
