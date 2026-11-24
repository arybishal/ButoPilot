package com.autopilot.android.feature.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MarkdownChangelogView(
    markdownText: String,
    modifier: Modifier = Modifier,
    textColor: Color = Color(0xFF1E293B)
) {
    val lines = markdownText.lines()
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> {
                    Spacer(modifier = Modifier.height(4.dp))
                }
                trimmed.startsWith("### ") -> {
                    Text(
                        text = parseInlineMarkdown(trimmed.removePrefix("### ")),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }
                trimmed.startsWith("## ") -> {
                    Text(
                        text = parseInlineMarkdown(trimmed.removePrefix("## ")),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }
                trimmed.startsWith("# ") -> {
                    Text(
                        text = parseInlineMarkdown(trimmed.removePrefix("# ")),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = textColor
                    )
                }
                trimmed == "---" || trimmed == "***" -> {
                    HorizontalDivider(
                        color = Color(0xFFE2E8F0),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    val bulletContent = trimmed.substring(2)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Text(
                            text = "• ",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2563EB)
                        )
                        Text(
                            text = parseInlineMarkdown(bulletContent),
                            fontSize = 13.sp,
                            color = textColor,
                            lineHeight = 18.sp
                        )
                    }
                }
                trimmed.matches(Regex("^\\d+\\.\\s.*")) -> {
                    val dotIdx = trimmed.indexOf('.')
                    val num = trimmed.substring(0, dotIdx + 1)
                    val content = trimmed.substring(dotIdx + 1).trim()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Text(
                            text = "$num ",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2563EB)
                        )
                        Text(
                            text = parseInlineMarkdown(content),
                            fontSize = 13.sp,
                            color = textColor,
                            lineHeight = 18.sp
                        )
                    }
                }
                else -> {
                    Text(
                        text = parseInlineMarkdown(trimmed),
                        fontSize = 13.sp,
                        color = textColor,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

/**
 * Parses inline markdown elements such as `**bold**`, `*italic*`, and `` `code` ``.
 */
fun parseInlineMarkdown(raw: String): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < raw.length) {
            when {
                // Bold: **text**
                raw.startsWith("**", i) -> {
                    val end = raw.indexOf("**", i + 2)
                    if (end != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(raw.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(raw[i])
                        i++
                    }
                }
                // Inline code: `text`
                raw.startsWith("`", i) -> {
                    val end = raw.indexOf("`", i + 1)
                    if (end != -1) {
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = Color(0xFFF1F5F9),
                                color = Color(0xFF0F172A)
                            )
                        ) {
                            append(" " + raw.substring(i + 1, end) + " ")
                        }
                        i = end + 1
                    } else {
                        append(raw[i])
                        i++
                    }
                }
                // Italic: *text*
                raw.startsWith("*", i) -> {
                    val end = raw.indexOf("*", i + 1)
                    if (end != -1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(raw.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(raw[i])
                        i++
                    }
                }
                else -> {
                    append(raw[i])
                    i++
                }
            }
        }
    }
}
