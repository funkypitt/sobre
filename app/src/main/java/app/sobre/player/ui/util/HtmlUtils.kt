package app.sobre.player.ui.util

import android.text.Html
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color

fun htmlToPlainText(html: String): String {
    if (html.isBlank()) return ""
    val spanned = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
    return spanned.toString().trim()
}

private val URL_REGEX = Regex("""https?://[^\s<>"]+""")

@Composable
fun LinkedText(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
    maxLines: Int = Int.MAX_VALUE
) {
    val uriHandler = LocalUriHandler.current
    val annotated = buildLinkedString(text)

    ClickableText(
        text = annotated,
        modifier = modifier,
        style = style,
        maxLines = maxLines,
        onClick = { offset ->
            annotated.getStringAnnotations("URL", offset, offset)
                .firstOrNull()?.let { uriHandler.openUri(it.item) }
        }
    )
}

private fun buildLinkedString(text: String): AnnotatedString {
    return buildAnnotatedString {
        var lastIndex = 0
        URL_REGEX.findAll(text).forEach { match ->
            append(text.substring(lastIndex, match.range.first))
            pushStringAnnotation("URL", match.value)
            withStyle(SpanStyle(color = Color.DarkGray, textDecoration = TextDecoration.Underline)) {
                append(match.value)
            }
            pop()
            lastIndex = match.range.last + 1
        }
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }
}
