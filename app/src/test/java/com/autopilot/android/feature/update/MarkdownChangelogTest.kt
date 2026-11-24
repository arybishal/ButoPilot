package com.autopilot.android.feature.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownChangelogTest {

    @Test
    fun testParseInlineBoldText() {
        val input = "This is **bold** text and normal text"
        val parsed = parseInlineMarkdown(input)
        assertEquals("This is bold text and normal text", parsed.text)
        assertTrue(parsed.spanStyles.isNotEmpty())
    }

    @Test
    fun testParseInlineCode() {
        val input = "Run `gradlew build` to compile"
        val parsed = parseInlineMarkdown(input)
        assertEquals("Run  gradlew build  to compile", parsed.text)
    }

    @Test
    fun testPlainTextWithoutMarkdown() {
        val input = "Simple plain text changelog message"
        val parsed = parseInlineMarkdown(input)
        assertEquals(input, parsed.text)
        assertTrue(parsed.spanStyles.isEmpty())
    }
}
