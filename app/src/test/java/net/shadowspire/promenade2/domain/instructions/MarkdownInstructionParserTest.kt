package net.shadowspire.promenade2.domain.instructions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownInstructionParserTest {
    @Test
    fun parsesMarkdownInstructionBlocks() {
        val document = MarkdownInstructionParser.parse(
            """
            # Grand Square

            Use **strong timing** and *soft hands*.
            - Face partner
            - Balance `left`
            > Caller note

            ```cue
            wait 8
            swing
            ```
            See [reference](https://example.test).
            """.trimIndent(),
        )

        assertEquals(6, document.blocks.size)
        assertTrue(document.blocks[0] is MarkdownBlock.Heading)
        assertTrue(document.blocks[1] is MarkdownBlock.Paragraph)
        assertTrue(document.blocks[2] is MarkdownBlock.BulletList)
        assertTrue(document.blocks[3] is MarkdownBlock.BlockQuote)
        assertTrue(document.blocks[4] is MarkdownBlock.CodeBlock)
        assertTrue(document.blocks[5] is MarkdownBlock.Paragraph)

        val heading = document.blocks[0] as MarkdownBlock.Heading
        assertEquals(1, heading.level)
        assertEquals(listOf(MarkdownInline.Text("Grand Square")), heading.content)

        val paragraph = document.blocks[1] as MarkdownBlock.Paragraph
        assertEquals(
            listOf(
                MarkdownInline.Text("Use "),
                MarkdownInline.Strong("strong timing"),
                MarkdownInline.Text(" and "),
                MarkdownInline.Emphasis("soft hands"),
                MarkdownInline.Text("."),
            ),
            paragraph.content,
        )

        val list = document.blocks[2] as MarkdownBlock.BulletList
        assertEquals(2, list.items.size)
        assertEquals(listOf(MarkdownInline.Text("Face partner")), list.items[0])
        assertEquals(
            listOf(
                MarkdownInline.Text("Balance "),
                MarkdownInline.Code("left"),
            ),
            list.items[1],
        )

        val code = document.blocks[4] as MarkdownBlock.CodeBlock
        assertEquals("cue", code.language)
        assertEquals("wait 8\nswing", code.code)

        val linkParagraph = document.blocks[5] as MarkdownBlock.Paragraph
        assertEquals(
            listOf(
                MarkdownInline.Text("See "),
                MarkdownInline.Link("reference", "https://example.test"),
                MarkdownInline.Text("."),
            ),
            linkParagraph.content,
        )
    }

    @Test
    fun keepsPlainTextReadable() {
        val document = MarkdownInstructionParser.parse(
            """
            Caller says:
            Circle left 8
            Swing through
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                MarkdownBlock.Paragraph(
                    listOf(MarkdownInline.Text("Caller says: Circle left 8 Swing through")),
                ),
            ),
            document.blocks,
        )
    }

    @Test
    fun leavesMalformedMarkdownAsText() {
        val document = MarkdownInstructionParser.parse(
            "Use **strong and [reference]( without crashing",
        )

        assertEquals(
            listOf(
                MarkdownBlock.Paragraph(
                    listOf(MarkdownInline.Text("Use **strong and [reference]( without crashing")),
                ),
            ),
            document.blocks,
        )
    }

    @Test
    fun keepsUnclosedCodeFenceReadableAsCodeBlock() {
        val document = MarkdownInstructionParser.parse(
            """
            ```
            wait 8
            allemande
            """.trimIndent(),
        )

        assertEquals(1, document.blocks.size)
        val block = document.blocks.single()
        assertTrue(block is MarkdownBlock.CodeBlock)
        assertEquals("wait 8\nallemande", (block as MarkdownBlock.CodeBlock).code)
    }
}
