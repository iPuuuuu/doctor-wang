package com.ipuuuuu.doctorwang.ui.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

internal sealed interface MarkdownBlock {
    data class Heading(val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class BulletList(val items: List<String>) : MarkdownBlock
    data class Table(val rows: List<List<String>>) : MarkdownBlock
}

internal fun parseMedicalMarkdown(content: String): List<MarkdownBlock> {
    // Compose Text 不执行 HTML；额外移除标签，避免把提示注入或伪链接样式直接展示给用户。
    val sanitized = content.replace(Regex("<[^>]+>"), "").trim()
    val lines = sanitized.replace("\r\n", "\n").lines()
    val blocks = mutableListOf<MarkdownBlock>()
    var index = 0
    while (index < lines.size) {
        val line = lines[index].trim()
        when {
            line.isBlank() -> index++
            line.startsWith("#") -> {
                blocks += MarkdownBlock.Heading(line.trimStart('#').trim())
                index++
            }
            line.startsWith("- ") || line.startsWith("* ") -> {
                val items = mutableListOf<String>()
                while (index < lines.size) {
                    val item = lines[index].trim()
                    if (!item.startsWith("- ") && !item.startsWith("* ")) break
                    items += item.drop(2).trim()
                    index++
                }
                blocks += MarkdownBlock.BulletList(items)
            }
            isTableLine(line) && index + 1 < lines.size && isSeparatorLine(lines[index + 1]) -> {
                val rows = mutableListOf<List<String>>()
                rows += splitTableRow(line)
                index += 2
                while (index < lines.size && isTableLine(lines[index].trim())) {
                    rows += splitTableRow(lines[index])
                    index++
                }
                blocks += MarkdownBlock.Table(rows)
            }
            else -> {
                val paragraph = mutableListOf(line)
                index++
                while (index < lines.size) {
                    val next = lines[index].trim()
                    if (next.isBlank() || next.startsWith("#") || next.startsWith("- ") || next.startsWith("* ")) break
                    if (isTableLine(next) && index + 1 < lines.size && isSeparatorLine(lines[index + 1])) break
                    paragraph += next
                    index++
                }
                blocks += MarkdownBlock.Paragraph(paragraph.joinToString("\n"))
            }
        }
    }
    return blocks
}

private fun isTableLine(line: String): Boolean = line.count { it == '|' } >= 2
private fun isSeparatorLine(line: String): Boolean = splitTableRow(line).isNotEmpty() &&
    splitTableRow(line).all { cell -> cell.replace(":", "").all { it == '-' } }
private fun splitTableRow(line: String): List<String> = line.trim().trim('|').split('|').map { it.trim() }

@Composable
fun MedicalMarkdown(
    content: String,
    modifier: Modifier = Modifier,
) {
    SelectionContainer(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            parseMedicalMarkdown(content).forEach { block ->
                when (block) {
                    is MarkdownBlock.Heading -> Text(
                        text = inlineMarkdown(block.text),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    is MarkdownBlock.Paragraph -> Text(
                        text = inlineMarkdown(block.text),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    is MarkdownBlock.BulletList -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        block.items.forEach { item ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("•", color = MaterialTheme.colorScheme.primary)
                                Text(inlineMarkdown(item), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    is MarkdownBlock.Table -> MarkdownTable(block.rows)
                }
            }
        }
    }
}

@Composable
private fun MarkdownTable(rows: List<List<String>>) {
    Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
        rows.forEachIndexed { rowIndex, row ->
            Row {
                row.forEach { cell ->
                    Surface(
                        color = if (rowIndex == 0) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 0.dp,
                    ) {
                        Text(
                            text = cell,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (rowIndex == 0) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

private fun inlineMarkdown(text: String) = buildAnnotatedString {
    var cursor = 0
    val boldPattern = Regex("\\*\\*(.+?)\\*\\*")
    boldPattern.findAll(text).forEach { match ->
        append(text.substring(cursor, match.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
            append(match.groupValues[1])
        }
        cursor = match.range.last + 1
    }
    append(text.substring(cursor))
}
