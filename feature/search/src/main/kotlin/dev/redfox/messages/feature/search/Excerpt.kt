package dev.redfox.messages.feature.search

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import dev.redfox.messages.core.domain.SearchQuery

/** The part of a message shown in a result, and where the matches are in it ([start, end] offsets). */
@Immutable
data class Excerpt(val text: String, val marks: List<IntRange>) {
    fun annotated(color: Color): AnnotatedString = buildAnnotatedString {
        append(text)
        marks.forEach { addStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold), it.first, it.last + 1) }
    }
}

/**
 * The part of [text] around the first match (so a match deep in a long SMS is visible), on one
 * line, with every match of [query] in it marked. Pure, so it runs off the main thread.
 */
internal fun excerpt(text: String, query: SearchQuery): Excerpt {
    val oneLine = text.replace('\n', ' ')
    val marks = query.matches(oneLine)
    val first = marks.firstOrNull()?.first ?: 0
    var start = if (first > CONTEXT_CHARS) first - CONTEXT_CHARS else 0
    // Never start on half an emoji.
    if (start > 0 && oneLine[start].isLowSurrogate()) start++
    val end = minOf(oneLine.length, start + WINDOW_CHARS)
    val prefix = if (start > 0) "…" else ""
    val shift = prefix.length - start
    return Excerpt(
        text = prefix + oneLine.substring(start, end),
        marks = marks.filter { it.first >= start && it.last < end }.map { (it.first + shift)..(it.last + shift) },
    )
}

private const val CONTEXT_CHARS = 28
private const val WINDOW_CHARS = 180
