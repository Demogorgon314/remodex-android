package com.emanueledipietro.remodex.feature.turn

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.viewinterop.AndroidView
import com.emanueledipietro.remodex.ui.theme.RemodexConversationShapes
import com.emanueledipietro.remodex.ui.theme.remodexConversationChrome
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import org.json.JSONObject

private sealed interface ConversationMarkdownSegment {
    data class Markdown(val text: String) : ConversationMarkdownSegment
    data class Mermaid(val source: String) : ConversationMarkdownSegment
}

private val mermaidFenceRegex = Regex(
    pattern = "```mermaid[^\\n]*\\n([\\s\\S]*?)```",
    option = RegexOption.IGNORE_CASE,
)

@Composable
internal fun ConversationRichMarkdownContent(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = remodexConversationChrome().bodyText,
    enablesSelection: Boolean = false,
) {
    val segments = remember(text) { parseConversationMarkdownSegments(text) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        segments.forEach { segment ->
            when (segment) {
                is ConversationMarkdownSegment.Markdown -> ConversationMarkdownTextSegment(
                    markdown = segment.text,
                    style = style,
                    color = color,
                    enablesSelection = enablesSelection,
                )

                is ConversationMarkdownSegment.Mermaid -> ConversationMermaidSegment(
                    source = segment.source,
                )
            }
        }
    }
}

private fun parseConversationMarkdownSegments(text: String): List<ConversationMarkdownSegment> {
    if (text.isBlank()) {
        return emptyList()
    }
    if (!text.contains("```mermaid", ignoreCase = true)) {
        return listOf(ConversationMarkdownSegment.Markdown(normalizeConversationMarkdown(text)))
    }

    val matches = mermaidFenceRegex.findAll(text).toList()
    if (matches.isEmpty()) {
        return listOf(ConversationMarkdownSegment.Markdown(normalizeConversationMarkdown(text)))
    }

    val segments = mutableListOf<ConversationMarkdownSegment>()
    var cursor = 0

    matches.forEach { match ->
        if (match.range.first > cursor) {
            appendMarkdownSegment(
                source = text.substring(cursor, match.range.first),
                target = segments,
            )
        }

        val mermaidSource = match.groups[1]
            ?.value
            ?.trim()
            .orEmpty()
        if (mermaidSource.isNotEmpty()) {
            segments += ConversationMarkdownSegment.Mermaid(mermaidSource)
        }
        cursor = match.range.last + 1
    }

    if (cursor < text.length) {
        appendMarkdownSegment(
            source = text.substring(cursor),
            target = segments,
        )
    }

    return if (segments.isEmpty()) {
        listOf(ConversationMarkdownSegment.Markdown(normalizeConversationMarkdown(text)))
    } else {
        segments
    }
}

private fun appendMarkdownSegment(
    source: String,
    target: MutableList<ConversationMarkdownSegment>,
) {
    val normalized = normalizeConversationMarkdown(source)
    if (normalized.isBlank()) {
        return
    }
    target += ConversationMarkdownSegment.Markdown(normalized)
}

private fun normalizeConversationMarkdown(text: String): String =
    text.replace("\r\n", "\n").trim('\n')

@Composable
private fun ConversationMarkdownTextSegment(
    markdown: String,
    style: TextStyle,
    color: Color,
    enablesSelection: Boolean,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val density = LocalDensity.current
    val chrome = remodexConversationChrome()
    val textSizePx = remember(style.fontSize, density) {
        if (style.fontSize.isSpecified) {
            with(density) { style.fontSize.toPx() }
        } else {
            Float.NaN
        }
    }
    val lineHeightExtra = remember(style.lineHeight, textSizePx, density) {
        if (style.lineHeight.isSpecified && !textSizePx.isNaN()) {
            (with(density) { style.lineHeight.toPx() } - textSizePx).coerceAtLeast(0f)
        } else {
            0f
        }
    }
    val markwon = remember(context, uriHandler) {
        Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(
                object : AbstractMarkwonPlugin() {
                    override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                        builder.linkResolver { _, link ->
                            val scheme = Uri.parse(link).scheme
                            if (!scheme.isNullOrBlank()) {
                                uriHandler.openUri(link)
                            }
                        }
                    }
                },
            )
            .build()
    }

    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { viewContext ->
            TextView(viewContext).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                includeFontPadding = false
                setLineSpacing(0f, 1f)
                setPadding(0, 0, 0, 0)
                highlightColor = AndroidColor.TRANSPARENT
                linksClickable = !enablesSelection
                isFocusable = false
                isClickable = false
                isLongClickable = enablesSelection
            }
        },
        update = { textView ->
            textView.setTextColor(color.toArgb())
            textView.setLinkTextColor(chrome.accent.toArgb())
            if (!textSizePx.isNaN()) {
                textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
            }
            textView.setLineSpacing(lineHeightExtra, 1f)
            textView.setTextIsSelectable(enablesSelection)
            textView.movementMethod = if (enablesSelection) {
                null
            } else {
                LinkMovementMethod.getInstance()
            }
            markwon.setMarkdown(textView, markdown)
        },
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ConversationMermaidSegment(
    source: String,
) {
    val chrome = remodexConversationChrome()
    val isDark = isSystemInDarkTheme()
    val density = LocalDensity.current
    var contentHeightPx by remember(source) { mutableStateOf(0) }
    val minHeight = 120.dp
    val resolvedHeight = remember(contentHeightPx, density) {
        if (contentHeightPx <= 0) {
            minHeight
        } else {
            with(density) { contentHeightPx.toDp() }.coerceAtLeast(minHeight)
        }
    }
    val mermaidHtml = remember(source, chrome, isDark) {
        buildMermaidHtml(
            source = source,
            isDark = isDark,
            accentColor = chrome.accent.toArgb(),
            textColor = chrome.bodyText.toArgb(),
            borderColor = chrome.subtleBorder.toArgb(),
            surfaceColor = chrome.panelSurface.toArgb(),
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight),
        color = chrome.panelSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, chrome.subtleBorder),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(min = 0.dp)
                .heightIn(min = minHeight)
                .padding(8.dp),
            factory = { context ->
                WebView(context).apply {
                    setBackgroundColor(AndroidColor.TRANSPARENT)
                    overScrollMode = WebView.OVER_SCROLL_NEVER
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    settings.javaScriptEnabled = true
                    settings.allowFileAccess = true
                    settings.domStorageEnabled = true
                    addJavascriptInterface(
                        MermaidHeightBridge { heightPx ->
                            contentHeightPx = heightPx
                        },
                        "AndroidHeight",
                    )
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {}
                }
            },
            update = { webView ->
                webView.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    with(density) { resolvedHeight.roundToPx() },
                )
                webView.loadDataWithBaseURL(
                    "file:///android_asset/",
                    mermaidHtml,
                    "text/html",
                    "utf-8",
                    null,
                )
            },
        )
    }
}

private fun buildMermaidHtml(
    source: String,
    isDark: Boolean,
    accentColor: Int,
    textColor: Int,
    borderColor: Int,
    surfaceColor: Int,
): String {
    val theme = if (isDark) "dark" else "neutral"
    val escapedSource = JSONObject.quote(source)
    val accentCss = toCssColor(accentColor)
    val textCss = toCssColor(textColor)
    val borderCss = toCssColor(borderColor)
    val surfaceCss = toCssColor(surfaceColor)
    return """
        <!doctype html>
        <html>
          <head>
            <meta charset="utf-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1" />
            <style>
              html, body {
                margin: 0;
                padding: 0;
                background: transparent;
                color: $textCss;
                overflow: hidden;
              }
              body {
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
              }
              #root {
                box-sizing: border-box;
                width: 100%;
                min-height: 104px;
                padding: 10px 12px;
                border-radius: 14px;
                background: $surfaceCss;
                border: 1px solid $borderCss;
              }
              #fallback {
                display: none;
                white-space: pre-wrap;
                font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
                font-size: 13px;
                line-height: 1.45;
              }
              svg {
                max-width: 100%;
                height: auto;
              }
              a {
                color: $accentCss;
              }
            </style>
            <script src="mermaid.min.js"></script>
          </head>
          <body>
            <div id="root"></div>
            <script>
              (async function renderMermaid() {
                const root = document.getElementById('root');
                const source = $escapedSource;
                try {
                  mermaid.initialize({
                    startOnLoad: false,
                    securityLevel: 'strict',
                    theme: '$theme'
                  });
                  const result = await mermaid.render(
                    'mermaid-' + Math.random().toString(36).slice(2),
                    source
                  );
                  root.innerHTML = result.svg;
                } catch (error) {
                  root.innerHTML = '<pre id="fallback"></pre>';
                  const fallback = document.getElementById('fallback');
                  fallback.style.display = 'block';
                  fallback.textContent = source;
                } finally {
                  requestAnimationFrame(function() {
                    const height = Math.ceil(
                      root.getBoundingClientRect().height ||
                      document.documentElement.scrollHeight ||
                      document.body.scrollHeight ||
                      120
                    );
                    if (window.AndroidHeight && window.AndroidHeight.postHeight) {
                      window.AndroidHeight.postHeight(height);
                    }
                  });
                }
              })();
            </script>
          </body>
        </html>
    """.trimIndent()
}

private fun toCssColor(argb: Int): String {
    val alpha = ((argb ushr 24) and 0xFF) / 255f
    val red = (argb ushr 16) and 0xFF
    val green = (argb ushr 8) and 0xFF
    val blue = argb and 0xFF
    return "rgba($red, $green, $blue, $alpha)"
}

private class MermaidHeightBridge(
    private val onHeight: (Int) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun postHeight(heightPx: Int) {
        if (heightPx <= 0) {
            return
        }
        mainHandler.post {
            onHeight(heightPx)
        }
    }
}
