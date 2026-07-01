package net.shadowspire.promenade2.domain.instructions

data class MarkdownInstructionDocument(
    val blocks: List<MarkdownBlock>,
)

sealed interface MarkdownBlock {
    data class Heading(
        val level: Int,
        val content: List<MarkdownInline>,
    ) : MarkdownBlock

    data class Paragraph(
        val content: List<MarkdownInline>,
    ) : MarkdownBlock

    data class BulletList(
        val items: List<List<MarkdownInline>>,
    ) : MarkdownBlock

    data class BlockQuote(
        val content: List<MarkdownInline>,
    ) : MarkdownBlock

    data class CodeBlock(
        val language: String?,
        val code: String,
    ) : MarkdownBlock
}

sealed interface MarkdownInline {
    data class Text(val value: String) : MarkdownInline
    data class Emphasis(val value: String) : MarkdownInline
    data class Strong(val value: String) : MarkdownInline
    data class Code(val value: String) : MarkdownInline
    data class Link(val label: String, val url: String) : MarkdownInline
}
