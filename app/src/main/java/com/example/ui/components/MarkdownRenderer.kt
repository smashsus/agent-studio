package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.Copy
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalSwissTokens
import com.example.ui.theme.SwissGeometry

private sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class BulletItem(val text: String) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock
}

@Composable
fun MarkdownMessageContent(
    markdown: String,
    onCopyCode: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalSwissTokens.current
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }

    SelectionContainer {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            blocks.forEach { block ->
                when (block) {
                    is MarkdownBlock.Heading -> {
                        val style = when (block.level) {
                            1 -> MaterialTheme.typography.headlineSmall
                            2 -> MaterialTheme.typography.titleMedium
                            else -> MaterialTheme.typography.titleSmall
                        }
                        Text(
                            text = formatInlineMarkdown(block.text, tokens.primaryAccent, tokens.surface2),
                            style = style,
                            color = tokens.primaryText
                        )
                    }
                    is MarkdownBlock.Paragraph -> {
                        Text(
                            text = formatInlineMarkdown(block.text, tokens.primaryAccent, tokens.surface2),
                            style = MaterialTheme.typography.bodyLarge,
                            color = tokens.primaryText
                        )
                    }
                    is MarkdownBlock.BulletItem -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodyLarge,
                                color = tokens.mutedText
                            )
                            Text(
                                text = formatInlineMarkdown(block.text, tokens.primaryAccent, tokens.surface2),
                                style = MaterialTheme.typography.bodyLarge,
                                color = tokens.primaryText,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    is MarkdownBlock.Quote -> {
                        Surface(
                            color = tokens.surface2.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(SwissGeometry.radiusSmall),
                            border = BorderStroke(0.75.dp, tokens.border.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = formatInlineMarkdown(block.text, tokens.primaryAccent, tokens.surface2),
                                style = MaterialTheme.typography.bodyMedium,
                                color = tokens.secondaryText,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    is MarkdownBlock.CodeBlock -> {
                        Surface(
                            color = tokens.surface1,
                            shape = RoundedCornerShape(SwissGeometry.radiusStandard),
                            border = BorderStroke(0.75.dp, tokens.border.copy(alpha = 0.55f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = block.language.ifBlank { "code" },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tokens.secondaryText
                                    )
                                    IconButton(
                                        onClick = { onCopyCode(block.code) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = PhosphorIcons.Regular.Copy,
                                            contentDescription = "Copy code",
                                            tint = tokens.secondaryText,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                SwissDivider()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = block.code,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontFamily = JetBrainsMonoFamily
                                        ),
                                        color = tokens.primaryText
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun parseMarkdownBlocks(input: String): List<MarkdownBlock> {
    val lines = input.replace("\r\n", "\n").lines()
    val blocks = mutableListOf<MarkdownBlock>()
    val paragraphBuf = mutableListOf<String>()
    var inCode = false
    var codeLang = ""
    val codeBuf = mutableListOf<String>()

    fun flushParagraph() {
        if (paragraphBuf.isNotEmpty()) {
            blocks.add(MarkdownBlock.Paragraph(paragraphBuf.joinToString("\n")))
            paragraphBuf.clear()
        }
    }

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.startsWith("```")) {
            if (!inCode) {
                flushParagraph()
                inCode = true
                codeLang = trimmed.removePrefix("```").trim()
                codeBuf.clear()
            } else {
                inCode = false
                blocks.add(MarkdownBlock.CodeBlock(codeLang, codeBuf.joinToString("\n")))
                codeLang = ""
                codeBuf.clear()
            }
            continue
        }

        if (inCode) {
            codeBuf.add(line)
            continue
        }

        when {
            trimmed.isEmpty() -> flushParagraph()
            trimmed == "---" || trimmed == "***" -> flushParagraph()
            trimmed.startsWith("### ") -> {
                flushParagraph()
                blocks.add(MarkdownBlock.Heading(3, trimmed.removePrefix("### ").trim()))
            }
            trimmed.startsWith("## ") -> {
                flushParagraph()
                blocks.add(MarkdownBlock.Heading(2, trimmed.removePrefix("## ").trim()))
            }
            trimmed.startsWith("# ") -> {
                flushParagraph()
                blocks.add(MarkdownBlock.Heading(1, trimmed.removePrefix("# ").trim()))
            }
            trimmed.startsWith("> ") -> {
                flushParagraph()
                blocks.add(MarkdownBlock.Quote(trimmed.removePrefix("> ").trim()))
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                flushParagraph()
                blocks.add(MarkdownBlock.BulletItem(trimmed.drop(2).trim()))
            }
            else -> paragraphBuf.add(line)
        }
    }

    if (inCode && codeBuf.isNotEmpty()) {
        blocks.add(MarkdownBlock.CodeBlock(codeLang, codeBuf.joinToString("\n")))
    }
    flushParagraph()
    return blocks
}

private fun formatInlineMarkdown(
    text: String,
    accentColor: androidx.compose.ui.graphics.Color,
    codeBg: androidx.compose.ui.graphics.Color
): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            if (text.startsWith("**", i)) {
                val end = text.indexOf("**", i + 2)
                if (end > i + 2) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold))
                    append(text.substring(i + 2, end))
                    pop()
                    i = end + 2
                    continue
                }
            }
            if (text[i] == '`') {
                val end = text.indexOf('`', i + 1)
                if (end > i + 1) {
                    pushStyle(
                        SpanStyle(
                            fontFamily = JetBrainsMonoFamily,
                            background = codeBg,
                            color = accentColor
                        )
                    )
                    append(" ${text.substring(i + 1, end)} ")
                    pop()
                    i = end + 1
                    continue
                }
            }
            if (text[i] == '_' && i + 1 < text.length && text[i + 1] != ' ') {
                val end = text.indexOf('_', i + 1)
                if (end > i + 1) {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(text.substring(i + 1, end))
                    pop()
                    i = end + 1
                    continue
                }
            }
            append(text[i])
            i++
        }
    }
}
