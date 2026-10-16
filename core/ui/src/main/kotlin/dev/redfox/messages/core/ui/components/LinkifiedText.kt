package dev.redfox.messages.core.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import dev.redfox.messages.core.ui.format.Links
import dev.redfox.obsidian.theme.ObsidianTheme

/**
 * Message text with its web links highlighted (accent, underlined) and tappable. Tapping opens
 * the link in the browser through the platform URI handler; the rest of the text stays plain.
 * Links are found once per text (remembered), not on every recomposition.
 */
@Composable
fun LinkifiedText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val linkColor = ObsidianTheme.colors.accentLight
    val annotated = remember(text, linkColor) { linkify(text, linkColor) }
    Text(annotated, style = style, color = color, modifier = modifier)
}

private fun linkify(text: String, linkColor: Color): AnnotatedString {
    val links = Links.find(text)
    if (links.isEmpty()) return AnnotatedString(text)
    val styles = TextLinkStyles(
        style = SpanStyle(color = linkColor, fontWeight = FontWeight.Medium, textDecoration = TextDecoration.Underline),
        pressedStyle = SpanStyle(color = linkColor, background = linkColor.copy(alpha = 0.18f)),
    )
    return buildAnnotatedString {
        var cursor = 0
        links.forEach { link ->
            append(text, cursor, link.range.first)
            withLink(LinkAnnotation.Url(link.url, styles)) {
                append(text, link.range.first, link.range.last + 1)
            }
            cursor = link.range.last + 1
        }
        append(text, cursor, text.length)
    }
}
