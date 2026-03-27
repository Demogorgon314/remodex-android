package com.emanueledipietro.remodex.feature.turn

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.load
import com.github.chrisbanes.photoview.PhotoView
import com.emanueledipietro.remodex.ui.theme.RemodexConversationShapes
import com.emanueledipietro.remodex.ui.theme.remodexConversationChrome
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import org.json.JSONObject

internal sealed interface ConversationMarkdownSegment {
    data class Markdown(val text: String) : ConversationMarkdownSegment
    data class CodeBlock(
        val code: String,
        val language: String?,
    ) : ConversationMarkdownSegment
    data class Mermaid(val source: String) : ConversationMarkdownSegment
    data class Image(
        val url: String,
        val altText: String?,
        val title: String?,
    ) : ConversationMarkdownSegment
}

private sealed interface ConversationMarkdownPreview {
    data class Image(
        val url: String,
        val altText: String?,
        val title: String?,
    ) : ConversationMarkdownPreview

    data class Mermaid(
        val source: String,
        val title: String,
    ) : ConversationMarkdownPreview
}

private val mermaidFenceRegex = Regex(
    pattern = "```mermaid[^\\n]*\\n([\\s\\S]*?)```",
    option = RegexOption.IGNORE_CASE,
)
private val markdownImageTitleRegex = Regex("""^(.*)\s+(?:"([^"]*)"|'([^']*)')$""")

private data class ConversationMarkdownTextRenderToken(
    val markdownToken: String,
    val textColorArgb: Int,
    val linkColorArgb: Int,
    val codeBackgroundColorArgb: Int,
    val codeBlockBackgroundColorArgb: Int,
    val blockMarginPx: Int,
    val codeBlockMarginPx: Int,
    val textSizePx: Float?,
    val lineHeightExtra: Float,
    val enablesSelection: Boolean,
)

private const val ConversationMarkdownRenderTokenSampleSize = 16

internal fun conversationMarkdownRenderToken(text: String): String {
    if (text.isEmpty()) {
        return "0|||"
    }

    fun sample(startIndex: Int): String {
        val length = text.length
        val safeStart = startIndex.coerceIn(0, (length - 1).coerceAtLeast(0))
        val safeEnd = (safeStart + ConversationMarkdownRenderTokenSampleSize).coerceAtMost(length)
        val builder = StringBuilder((safeEnd - safeStart) * 2)
        for (index in safeStart until safeEnd) {
            val code = text[index].code
            builder.append(code.toString(16))
            builder.append('-')
        }
        return builder.toString()
    }

    val midStart = ((text.length - ConversationMarkdownRenderTokenSampleSize) / 2).coerceAtLeast(0)
    val endStart = (text.length - ConversationMarkdownRenderTokenSampleSize).coerceAtLeast(0)
    return buildString {
        append(text.length)
        append('|')
        append(sample(startIndex = 0))
        append('|')
        append(sample(startIndex = midStart))
        append('|')
        append(sample(startIndex = endStart))
    }
}

@Composable
internal fun ConversationRichMarkdownContent(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = remodexConversationChrome().bodyText,
    enablesSelection: Boolean = false,
    onLongPress: ((IntOffset) -> Unit)? = null,
) {
    val segments = remember(text) { parseConversationMarkdownSegments(text) }
    var preview by remember(text) { mutableStateOf<ConversationMarkdownPreview?>(null) }

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
                    onLongPress = onLongPress,
                )

                is ConversationMarkdownSegment.CodeBlock -> ConversationMarkdownCodeBlockSegment(
                    code = segment.code,
                    style = style,
                    enablesSelection = enablesSelection,
                    onLongPress = onLongPress,
                )

                is ConversationMarkdownSegment.Mermaid -> ConversationMermaidSegment(
                    source = segment.source,
                    onLongPress = onLongPress,
                    onPreview = {
                        preview = ConversationMarkdownPreview.Mermaid(
                            source = segment.source,
                            title = "Diagram",
                        )
                    },
                )

                is ConversationMarkdownSegment.Image -> ConversationMarkdownImageSegment(
                    url = segment.url,
                    altText = segment.altText,
                    title = segment.title,
                    onLongPress = onLongPress,
                    onPreview = {
                        preview = ConversationMarkdownPreview.Image(
                            url = segment.url,
                            altText = segment.altText,
                            title = segment.title,
                        )
                    },
                )
            }
        }
    }

    preview?.let { resolvedPreview ->
        ConversationMarkdownPreviewDialog(
            preview = resolvedPreview,
            onDismiss = { preview = null },
        )
    }
}

internal fun parseConversationMarkdownSegments(text: String): List<ConversationMarkdownSegment> {
    if (text.isBlank()) {
        return emptyList()
    }
    if (!text.contains("```mermaid", ignoreCase = true)) {
        return buildList {
            appendMarkdownSegment(
                source = text,
                target = this,
            )
            if (isEmpty()) {
                add(ConversationMarkdownSegment.Markdown(normalizeConversationMarkdown(text)))
            }
        }
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

    val markdownLines = mutableListOf<String>()
    val codeLines = mutableListOf<String>()
    var insideCodeFence = false
    var codeFenceStartLine: String? = null
    var codeFenceLanguage: String? = null

    fun flushMarkdown() {
        val markdown = markdownLines.joinToString("\n").trim('\n')
        if (markdown.isNotBlank()) {
            target += ConversationMarkdownSegment.Markdown(markdown)
        }
        markdownLines.clear()
    }

    fun flushCodeBlock() {
        val code = codeLines.joinToString("\n").trimEnd('\n')
        if (code.isNotEmpty()) {
            target += ConversationMarkdownSegment.CodeBlock(
                code = code,
                language = codeFenceLanguage,
            )
        }
        codeLines.clear()
        codeFenceStartLine = null
        codeFenceLanguage = null
    }

    normalized.lines().forEach { rawLine ->
        val trimmed = rawLine.trim()
        val fenceInfo = fenceInfoOrNull(trimmed)

        if (insideCodeFence) {
            if (fenceInfo != null) {
                flushMarkdown()
                flushCodeBlock()
                insideCodeFence = false
            } else {
                codeLines += rawLine
            }
            return@forEach
        }

        if (fenceInfo == null) {
            parseStandaloneMarkdownImage(trimmed)?.let { image ->
                flushMarkdown()
                target += image
                return@forEach
            }
        }

        if (fenceInfo != null) {
            insideCodeFence = true
            codeFenceStartLine = rawLine
            codeFenceLanguage = fenceInfo.ifBlank { null }
        } else {
            markdownLines += rawLine
        }
    }

    if (insideCodeFence) {
        codeFenceStartLine?.let(markdownLines::add)
        markdownLines.addAll(codeLines)
    }

    flushMarkdown()
}

private fun normalizeConversationMarkdown(text: String): String =
    text.replace("\r\n", "\n").trim('\n')

private fun fenceInfoOrNull(trimmedLine: String): String? {
    if (!trimmedLine.startsWith("```")) {
        return null
    }
    return trimmedLine.removePrefix("```").trim()
}

private fun parseStandaloneMarkdownImage(line: String): ConversationMarkdownSegment.Image? {
    if (!line.startsWith("![") || !line.endsWith(")")) {
        return null
    }
    val altTextEnd = line.indexOf("](")
    if (altTextEnd < 0) {
        return null
    }

    val altText = line.substring(startIndex = 2, endIndex = altTextEnd).ifBlank { null }
    var payload = line.substring(startIndex = altTextEnd + 2, endIndex = line.length - 1).trim()
    if (payload.isBlank()) {
        return null
    }

    var title: String? = null
    markdownImageTitleRegex.matchEntire(payload)?.let { match ->
        payload = match.groupValues[1].trim()
        title = match.groupValues[2].ifBlank {
            match.groupValues[3].ifBlank { null }
        }
    }

    val url = if (payload.startsWith("<") && payload.endsWith(">") && payload.length > 2) {
        payload.substring(1, payload.length - 1).trim()
    } else {
        payload
    }.takeIf { value -> value.isNotBlank() }
        ?: return null

    return ConversationMarkdownSegment.Image(
        url = url,
        altText = altText,
        title = title,
    )
}

@Composable
private fun ConversationMarkdownTextSegment(
    markdown: String,
    style: TextStyle,
    color: Color,
    enablesSelection: Boolean,
    onLongPress: ((IntOffset) -> Unit)?,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val density = LocalDensity.current
    val chrome = remodexConversationChrome()
    val isDark = isSystemInDarkTheme()
    val selectionHighlightColor = remember(context) { platformSelectionHighlightColor(context) }
    val lastTouchOffset = remember { intArrayOf(0, 0) }
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
    val codeBackgroundColor = remember(chrome, isDark) {
        if (isDark) chrome.panelSurfaceStrong else chrome.nestedSurface
    }
    val codeBlockBackgroundColor = remember(chrome, isDark) {
        if (isDark) chrome.panelSurfaceStrong else chrome.nestedSurface
    }
    val blockMarginPx = remember(density) {
        with(density) { 14.dp.roundToPx() }
    }
    val codeBlockMarginPx = remember(density) {
        with(density) { 6.dp.roundToPx() }
    }
    val renderToken = remember(
        markdown,
        color,
        chrome.accent,
        codeBackgroundColor,
        codeBlockBackgroundColor,
        blockMarginPx,
        codeBlockMarginPx,
        textSizePx,
        lineHeightExtra,
        enablesSelection,
    ) {
        ConversationMarkdownTextRenderToken(
            markdownToken = conversationMarkdownRenderToken(markdown),
            textColorArgb = color.toArgb(),
            linkColorArgb = chrome.accent.toArgb(),
            codeBackgroundColorArgb = codeBackgroundColor.toArgb(),
            codeBlockBackgroundColorArgb = codeBlockBackgroundColor.toArgb(),
            blockMarginPx = blockMarginPx,
            codeBlockMarginPx = codeBlockMarginPx,
            textSizePx = textSizePx.takeUnless(Float::isNaN),
            lineHeightExtra = lineHeightExtra,
            enablesSelection = enablesSelection,
        )
    }
    val markwon = remember(
        context,
        uriHandler,
        renderToken.textColorArgb,
        renderToken.linkColorArgb,
        renderToken.codeBackgroundColorArgb,
        renderToken.codeBlockBackgroundColorArgb,
        renderToken.blockMarginPx,
        renderToken.codeBlockMarginPx,
    ) {
        Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(
                object : AbstractMarkwonPlugin() {
                    override fun configureTheme(builder: MarkwonTheme.Builder) {
                        builder
                            .linkColor(renderToken.linkColorArgb)
                            .codeTextColor(renderToken.textColorArgb)
                            .codeBlockTextColor(renderToken.textColorArgb)
                            .codeBackgroundColor(renderToken.codeBackgroundColorArgb)
                            .codeBlockBackgroundColor(renderToken.codeBlockBackgroundColorArgb)
                            .blockMargin(renderToken.blockMarginPx)
                            .codeBlockMargin(renderToken.codeBlockMarginPx)
                    }

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
                highlightColor = if (enablesSelection) selectionHighlightColor else AndroidColor.TRANSPARENT
                linksClickable = !enablesSelection
                isFocusable = false
                isClickable = false
                isLongClickable = enablesSelection || onLongPress != null
            }
        },
        update = { textView ->
            val previousToken = textView.tag as? ConversationMarkdownTextRenderToken
            textView.setOnTouchListener(
                if (!enablesSelection && onLongPress != null) {
                    { _, event ->
                        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                            lastTouchOffset[0] = event.x.toInt()
                            lastTouchOffset[1] = event.y.toInt()
                        }
                        false
                    }
                } else {
                    null
                },
            )
            textView.setOnLongClickListener(
                if (!enablesSelection && onLongPress != null) {
                    {
                        onLongPress(IntOffset(x = lastTouchOffset[0], y = lastTouchOffset[1]))
                        true
                    }
                } else {
                    null
                },
            )
            textView.isLongClickable = enablesSelection || onLongPress != null
            textView.highlightColor = if (renderToken.enablesSelection) {
                selectionHighlightColor
            } else {
                AndroidColor.TRANSPARENT
            }
            if (previousToken != renderToken) {
                textView.setTextColor(renderToken.textColorArgb)
                textView.setLinkTextColor(renderToken.linkColorArgb)
                renderToken.textSizePx?.let { resolvedTextSizePx ->
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, resolvedTextSizePx)
                }
                textView.setLineSpacing(renderToken.lineHeightExtra, 1f)
                textView.setTextIsSelectable(renderToken.enablesSelection)
                textView.movementMethod = if (renderToken.enablesSelection) {
                    null
                } else {
                    LinkMovementMethod.getInstance()
                }
                markwon.setMarkdown(textView, markdown)
                textView.tag = renderToken
            }
        },
    )
}

@Composable
private fun ConversationMarkdownCodeBlockSegment(
    code: String,
    style: TextStyle,
    enablesSelection: Boolean,
    onLongPress: ((IntOffset) -> Unit)?,
) {
    val chrome = remodexConversationChrome()
    val textStyle = remember(style) {
        style.copy(fontFamily = FontFamily.Monospace)
    }
    val contentModifier = if (!enablesSelection && onLongPress != null) {
        Modifier
            .fillMaxWidth()
            .pointerInput(onLongPress) {
                detectTapGestures(
                    onLongPress = { offset ->
                        onLongPress(
                            IntOffset(
                                x = offset.x.toInt(),
                                y = offset.y.toInt(),
                            ),
                        )
                    },
                )
            }
            .semantics {
                onLongClick {
                    onLongPress(IntOffset.Zero)
                    true
                }
            }
    } else {
        Modifier.fillMaxWidth()
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = chrome.nestedSurface,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        if (enablesSelection) {
            SelectionContainer {
                androidx.compose.material3.Text(
                    text = code,
                    modifier = contentModifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = textStyle,
                    color = chrome.bodyText,
                )
            }
        } else {
            androidx.compose.material3.Text(
                text = code,
                modifier = contentModifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = textStyle,
                color = chrome.bodyText,
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ConversationMermaidSegment(
    source: String,
    onLongPress: ((IntOffset) -> Unit)?,
    onPreview: () -> Unit,
) {
    val view = LocalView.current
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
        Box {
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

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(16.dp))
                    .markdownPreviewGestureModifier(
                        onTap = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onPreview()
                        },
                        onLongPress = onLongPress,
                    ),
            )
        }
    }
}

@Composable
private fun ConversationMarkdownImageSegment(
    url: String,
    altText: String?,
    title: String?,
    onLongPress: ((IntOffset) -> Unit)?,
    onPreview: () -> Unit,
) {
    val view = LocalView.current
    val chrome = remodexConversationChrome()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .markdownPreviewGestureModifier(
                onTap = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onPreview()
                },
                onLongPress = onLongPress,
            ),
        color = chrome.panelSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, chrome.subtleBorder),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AsyncImage(
                model = url,
                contentDescription = altText ?: title ?: "Image",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 320.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(chrome.nestedSurface),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

private fun Modifier.markdownPreviewGestureModifier(
    onTap: () -> Unit,
    onLongPress: ((IntOffset) -> Unit)?,
): Modifier {
    val gestureModifier = if (onLongPress != null) {
        pointerInput(onTap, onLongPress) {
            detectTapGestures(
                onTap = { onTap() },
                onLongPress = { offset ->
                    onLongPress(
                        IntOffset(
                            x = offset.x.toInt(),
                            y = offset.y.toInt(),
                        ),
                    )
                },
            )
        }.semantics {
            onClick {
                onTap()
                true
            }
            onLongClick {
                onLongPress(IntOffset.Zero)
                true
            }
        }
    } else {
        clickable(onClick = onTap)
    }

    return this.then(gestureModifier)
}

private fun platformSelectionHighlightColor(context: Context): Int {
    return TextView(context).highlightColor
}

@Composable
private fun ConversationMarkdownPreviewDialog(
    preview: ConversationMarkdownPreview,
    onDismiss: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            ConversationMarkdownPreviewBackground(
                modifier = Modifier.matchParentSize(),
            )

            when (preview) {
                is ConversationMarkdownPreview.Image -> ZoomableConversationImagePreview(
                    preview = preview,
                    modifier = Modifier.fillMaxSize(),
                )

                is ConversationMarkdownPreview.Mermaid -> ConversationMermaidPreview(
                    preview = preview,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ConversationMarkdownPreviewGlassChip(
                    shape = RoundedCornerShape(999.dp),
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Close preview",
                            tint = colorScheme.onSurface,
                        )
                    }
                }

                val title = when (preview) {
                    is ConversationMarkdownPreview.Image -> preview.title ?: preview.altText
                    is ConversationMarkdownPreview.Mermaid -> preview.title
                }
                if (!title.isNullOrBlank()) {
                    ConversationMarkdownPreviewGlassChip(
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        androidx.compose.material3.Text(
                            text = title,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = colorScheme.onSurface,
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ZoomableConversationImagePreview(
    preview: ConversationMarkdownPreview.Image,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            PhotoView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                maximumScale = 5f
                mediumScale = 2.5f
                minimumScale = 1f
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                setOnTouchListener { view, _ ->
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    false
                }
            }
        },
        update = { imageView ->
            imageView.contentDescription = preview.altText ?: preview.title ?: "Image preview"
            imageView.load(preview.url)
        },
    )
}

@Composable
private fun ConversationMermaidPreview(
    preview: ConversationMarkdownPreview.Mermaid,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        ConversationMermaidPreviewCard(
            source = preview.source,
            modifier = Modifier.fillMaxSize(),
            previewMode = true,
        )
    }
}

@Composable
private fun ConversationMarkdownPreviewBackground(
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier.background(colorScheme.background.copy(alpha = 0.94f)),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            colorScheme.surfaceBright.copy(alpha = 0.74f),
                            colorScheme.surface.copy(alpha = 0.58f),
                            colorScheme.background.copy(alpha = 0.82f),
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            colorScheme.primary.copy(alpha = 0.10f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.14f),
                            Color.White.copy(alpha = 0.05f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
    }
}

@Composable
private fun ConversationMarkdownPreviewGlassChip(
    shape: RoundedCornerShape,
    content: @Composable () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = shape,
        color = colorScheme.surface.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, colorScheme.onSurface.copy(alpha = 0.10f)),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier.background(
                Brush.linearGradient(
                    colors = listOf(
                        colorScheme.surfaceContainerHigh.copy(alpha = 0.58f),
                        colorScheme.surface.copy(alpha = 0.42f),
                    ),
                ),
            ),
        ) {
            content()
        }
    }
}

@Composable
private fun ConversationMermaidPreviewCard(
    source: String,
    modifier: Modifier = Modifier,
    previewMode: Boolean = false,
) {
    val chrome = remodexConversationChrome()
    val isDark = isSystemInDarkTheme()
    val density = LocalDensity.current
    var contentHeightPx by remember(source) { mutableStateOf(0) }
    val minHeight = 220.dp
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
            textColor = if (previewMode) Color.White.toArgb() else chrome.bodyText.toArgb(),
            borderColor = if (previewMode) Color.Transparent.toArgb() else chrome.subtleBorder.toArgb(),
            surfaceColor = if (previewMode) Color.Transparent.toArgb() else chrome.panelSurface.toArgb(),
            previewMode = previewMode,
        )
    }
    val androidViewModifier = if (previewMode) {
        Modifier.fillMaxSize()
    } else {
        Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .padding(12.dp)
    }

    val webContent: @Composable () -> Unit = {
        AndroidView(
            modifier = androidViewModifier,
            factory = { context ->
                WebView(context).apply {
                    setBackgroundColor(AndroidColor.TRANSPARENT)
                    overScrollMode = WebView.OVER_SCROLL_NEVER
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    settings.javaScriptEnabled = true
                    settings.allowFileAccess = true
                    settings.domStorageEnabled = true
                    settings.setSupportZoom(true)
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
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
                    if (previewMode) ViewGroup.LayoutParams.MATCH_PARENT else with(density) { resolvedHeight.roundToPx() },
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

    if (previewMode) {
        Box(modifier = modifier.fillMaxSize()) {
            webContent()
        }
    } else {
        Surface(
            modifier = modifier.heightIn(min = minHeight),
            color = chrome.panelSurface,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, chrome.subtleBorder),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
        ) {
            webContent()
        }
    }
}

private fun buildMermaidHtml(
    source: String,
    isDark: Boolean,
    accentColor: Int,
    textColor: Int,
    borderColor: Int,
    surfaceColor: Int,
    previewMode: Boolean = false,
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
                overflow: ${if (previewMode) "auto" else "hidden"};
                width: 100%;
                height: 100%;
              }
              body {
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                ${if (previewMode) "display: flex; align-items: center; justify-content: center;" else ""}
              }
              #root {
                box-sizing: border-box;
                width: ${if (previewMode) "auto" else "100%"};
                min-height: ${if (previewMode) "auto" else "104px"};
                padding: ${if (previewMode) "0" else "10px 12px"};
                border-radius: ${if (previewMode) "0" else "14px"};
                background: ${if (previewMode) "transparent" else surfaceCss};
                border: ${if (previewMode) "none" else "1px solid $borderCss"};
                display: flex;
                align-items: center;
                justify-content: center;
              }
              #fallback {
                display: none;
                white-space: pre-wrap;
                font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
                font-size: 13px;
                line-height: 1.45;
              }
              svg {
                max-width: ${if (previewMode) "none" else "100%"};
                max-height: ${if (previewMode) "none" else "auto"};
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
