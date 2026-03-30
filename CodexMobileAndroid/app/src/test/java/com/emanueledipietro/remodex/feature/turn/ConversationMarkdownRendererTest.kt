package com.emanueledipietro.remodex.feature.turn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationMarkdownRendererTest {
    @Test
    fun `render token stays stable for identical markdown`() {
        val markdown = """
            ## Status

            See [README](README.md) and run `./gradlew test`.
        """.trimIndent()

        assertEquals(
            conversationMarkdownRenderToken(markdown),
            conversationMarkdownRenderToken(markdown),
        )
    }

    @Test
    fun `render token changes when markdown body changes`() {
        val first = buildString {
            append("prefix-")
            repeat(64) { append('a') }
            append("-suffix")
        }
        val second = buildString {
            append("prefix-")
            repeat(32) { append('a') }
            repeat(32) { append('b') }
            append("-suffix")
        }

        assertNotEquals(
            conversationMarkdownRenderToken(first),
            conversationMarkdownRenderToken(second),
        )
    }

    @Test
    fun `render token changes when mermaid body changes outside prior sampled windows`() {
        val first = buildString {
            append("```mermaid\n")
            repeat(160) { append('a') }
            append("\n```")
        }
        val second = StringBuilder(first).apply {
            setCharAt("```mermaid\n".length + 20, 'b')
            setCharAt("```mermaid\n".length + 100, 'c')
        }.toString()

        assertNotEquals(
            conversationMarkdownRenderToken(first),
            conversationMarkdownRenderToken(second),
        )
    }

    @Test
    fun `markdown parser extracts fenced code blocks as standalone segments`() {
        val markdown = """
            Before paragraph

            ```kotlin
            val answer = 42
            println(answer)
            ```

            After paragraph with `inline code`
        """.trimIndent()

        val segments = parseConversationMarkdownSegments(markdown)

        assertEquals(3, segments.size)
        assertTrue(segments[0] is ConversationMarkdownSegment.Markdown)
        assertTrue(segments[1] is ConversationMarkdownSegment.CodeBlock)
        assertTrue(segments[2] is ConversationMarkdownSegment.Markdown)

        val codeBlock = segments[1] as ConversationMarkdownSegment.CodeBlock
        assertEquals("kotlin", codeBlock.language)
        assertEquals(
            """
            val answer = 42
            println(answer)
            """.trimIndent(),
            codeBlock.code,
        )
    }

    @Test
    fun `markdown parser keeps unmatched fence as markdown`() {
        val markdown = """
            Intro

            ```swift
            let value = 1
        """.trimIndent()

        val segments = parseConversationMarkdownSegments(markdown)

        assertEquals(1, segments.size)
        val onlySegment = segments.single()
        assertTrue(onlySegment is ConversationMarkdownSegment.Markdown)
        assertEquals(markdown, (onlySegment as ConversationMarkdownSegment.Markdown).text)
    }
}
