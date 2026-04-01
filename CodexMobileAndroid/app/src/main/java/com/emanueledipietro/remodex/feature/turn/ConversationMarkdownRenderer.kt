package com.emanueledipietro.remodex.feature.turn

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
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
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.compose.AsyncImage
import coil.load
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.github.chrisbanes.photoview.PhotoView
import com.emanueledipietro.remodex.model.decodeInlineImageDataUrlBytes
import com.emanueledipietro.remodex.platform.media.GalleryImageSaver
import com.emanueledipietro.remodex.ui.theme.RemodexConversationShapes
import com.emanueledipietro.remodex.ui.theme.remodexConversationChrome
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.syntax.Prism4jSyntaxHighlight
import io.noties.markwon.syntax.Prism4jThemeDarkula
import io.noties.markwon.syntax.Prism4jThemeDefault
import io.noties.markwon.syntax.SyntaxHighlightPlugin
import io.noties.prism4j.Prism4j
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

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

private data class ConversationMarkdownCodeBlockRenderToken(
    val markdownToken: String,
    val textColorArgb: Int,
    val textSizePx: Float?,
    val lineHeightExtra: Float,
    val enablesSelection: Boolean,
    val usesDarkSyntaxTheme: Boolean,
)

internal fun conversationMarkdownRenderToken(text: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
    return buildString(digest.size * 2) {
        digest.forEach { byte ->
            append(((byte.toInt() ushr 4) and 0xF).toString(16))
            append((byte.toInt() and 0xF).toString(16))
        }
    }
}

@Composable
private fun rememberConversationMarkdownSaveController(): ConversationMarkdownSaveController {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val coroutineScope = rememberCoroutineScope()
    var pendingSave by remember { mutableStateOf<PendingConversationGallerySave?>(null) }

    fun showToastMessage(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun performSave(request: PendingConversationGallerySave) {
        coroutineScope.launch {
            val result = GalleryImageSaver.saveBitmap(
                context = appContext,
                bitmap = request.bitmap,
                suggestedName = request.suggestedName,
            )
            result.fold(
                onSuccess = {
                    showToastMessage("Saved to Photos.")
                },
                onFailure = { error ->
                    showToastMessage(error.message ?: "Couldn't save this image.")
                },
            )
            request.onComplete()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val request = pendingSave
        pendingSave = null
        if (granted && request != null) {
            performSave(request)
        } else if (!granted) {
            showToastMessage("Storage permission is required to save images to Photos.")
            request?.onComplete()
        }
    }

    fun enqueueSave(request: PendingConversationGallerySave) {
        val permission = GalleryImageSaver.requiredWritePermission()
        if (
            permission != null &&
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingSave = request
            permissionLauncher.launch(permission)
            return
        }
        performSave(request)
    }

    return remember(context, appContext) {
        object : ConversationMarkdownSaveController {
            override fun saveBitmap(
                bitmap: Bitmap,
                suggestedName: String,
                onComplete: () -> Unit,
            ) {
                enqueueSave(
                    PendingConversationGallerySave(
                        bitmap = bitmap,
                        suggestedName = suggestedName,
                        onComplete = onComplete,
                    ),
                )
            }

            override fun saveImageFromUrl(
                imageUrl: String,
                suggestedName: String,
                onComplete: () -> Unit,
            ) {
                coroutineScope.launch {
                    val bitmap = loadBitmapForSave(
                        context = appContext,
                        imageUrl = imageUrl,
                    )
                    if (bitmap == null) {
                        showToastMessage("Couldn't load this image.")
                        onComplete()
                    } else {
                        enqueueSave(
                            PendingConversationGallerySave(
                                bitmap = bitmap,
                                suggestedName = suggestedName,
                                onComplete = onComplete,
                            ),
                        )
                    }
                }
            }

            override fun copyText(
                label: String,
                text: String,
            ) {
                val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
                clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
            }

            override fun showMessage(message: String) {
                showToastMessage(message)
            }
        }
    }
}

private data class PendingConversationGallerySave(
    val bitmap: Bitmap,
    val suggestedName: String,
    val onComplete: () -> Unit,
)

private data class ConversationMarkdownPreviewSaveState(
    val onSave: () -> Unit,
    val isLoading: Boolean,
)

private interface ConversationMarkdownSaveController {
    fun saveBitmap(
        bitmap: Bitmap,
        suggestedName: String,
        onComplete: () -> Unit = {},
    )

    fun saveImageFromUrl(
        imageUrl: String,
        suggestedName: String,
        onComplete: () -> Unit = {},
    )

    fun copyText(
        label: String,
        text: String,
    )

    fun showMessage(message: String)
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
    val saveController = rememberConversationMarkdownSaveController()
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
                    language = segment.language,
                    style = style,
                    enablesSelection = enablesSelection,
                    saveController = saveController,
                    onLongPress = onLongPress,
                )

                is ConversationMarkdownSegment.Mermaid -> ConversationMermaidSegment(
                    source = segment.source,
                    saveController = saveController,
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
                    saveController = saveController,
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
            saveController = saveController,
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

internal fun normalizeConversationMarkdownCodeLanguage(language: String?): String? {
    val normalized = language
        ?.trim()
        ?.lowercase()
        ?.takeIf(String::isNotBlank)
        ?: return null

    return when (normalized) {
        "c#" -> "csharp"
        "c++" -> "cpp"
        "cs" -> "csharp"
        "gradle", "groovy-gradle" -> "groovy"
        "js", "jsx", "mjs", "cjs", "ts", "tsx" -> "javascript"
        "json5", "jsonc" -> "json"
        "kt", "kts", "kotlin-script" -> "kotlin"
        "md", "mdx" -> "markdown"
        "objc" -> "c"
        "objective-c" -> "c"
        "objective-c++" -> "cpp"
        "py" -> "python"
        "rb" -> null
        "shell", "shell-session", "bash", "sh", "zsh", "console", "terminal" -> null
        "text", "plaintext", "plain", "txt" -> null
        "yml" -> "yaml"
        else -> normalized
    }
}

internal fun fencedCodeBlockMarkdown(
    code: String,
    language: String?,
): String {
    val normalizedLanguage = normalizeConversationMarkdownCodeLanguage(language)
    return buildString(code.length + (normalizedLanguage?.length ?: 0) + 8) {
        append("```")
        if (normalizedLanguage != null) {
            append(normalizedLanguage)
        }
        append('\n')
        append(code)
        if (!code.endsWith('\n')) {
            append('\n')
        }
        append("```")
    }
}

private fun adjustedCodeBlockTextStyle(style: TextStyle): TextStyle {
    val adjustedFontSize = if (style.fontSize.isSpecified) {
        (style.fontSize.value - 1f).coerceAtLeast(12f).sp
    } else {
        style.fontSize
    }
    val adjustedLineHeight = if (style.lineHeight.isSpecified) {
        (style.lineHeight.value - 1f).coerceAtLeast(adjustedFontSize.value).sp
    } else {
        style.lineHeight
    }

    return style.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = adjustedFontSize,
        lineHeight = adjustedLineHeight,
    )
}

private fun codeBlockHeaderLabel(language: String?): String {
    return language
        ?.trim()
        ?.ifBlank { null }
        ?: "code"
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
    language: String?,
    style: TextStyle,
    enablesSelection: Boolean,
    saveController: ConversationMarkdownSaveController,
    onLongPress: ((IntOffset) -> Unit)?,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val chrome = remodexConversationChrome()
    val isDark = isSystemInDarkTheme()
    val selectionHighlightColor = remember(context) { platformSelectionHighlightColor(context) }
    val lastTouchOffset = remember { intArrayOf(0, 0) }
    val languageLabel = remember(language) { codeBlockHeaderLabel(language) }
    var didCopy by remember(code, languageLabel) { mutableStateOf(false) }
    val normalizedLanguage = remember(language) {
        normalizeConversationMarkdownCodeLanguage(language)
    }
    val textStyle = remember(style) {
        adjustedCodeBlockTextStyle(style)
    }
    val textSizePx = remember(textStyle.fontSize, density) {
        if (textStyle.fontSize.isSpecified) {
            with(density) { textStyle.fontSize.toPx() }
        } else {
            Float.NaN
        }
    }
    val lineHeightExtra = remember(textStyle.lineHeight, textSizePx, density) {
        if (textStyle.lineHeight.isSpecified && !textSizePx.isNaN()) {
            (with(density) { textStyle.lineHeight.toPx() } - textSizePx).coerceAtLeast(0f)
        } else {
            0f
        }
    }
    val renderToken = remember(
        code,
        language,
        chrome.bodyText,
        textSizePx,
        lineHeightExtra,
        enablesSelection,
        isDark,
    ) {
        ConversationMarkdownCodeBlockRenderToken(
            markdownToken = conversationMarkdownRenderToken(
                fencedCodeBlockMarkdown(code = code, language = language),
            ),
            textColorArgb = chrome.bodyText.toArgb(),
            textSizePx = textSizePx.takeUnless(Float::isNaN),
            lineHeightExtra = lineHeightExtra,
            enablesSelection = enablesSelection,
            usesDarkSyntaxTheme = isDark,
        )
    }
    val prism4j = remember { RemodexPrism4jFactory.create() }
    val prism4jTheme = remember(isDark) {
        if (isDark) {
            Prism4jThemeDarkula.create(AndroidColor.TRANSPARENT)
        } else {
            Prism4jThemeDefault.create(AndroidColor.TRANSPARENT)
        }
    }
    val syntaxHighlight = remember(prism4j, prism4jTheme) {
        Prism4jSyntaxHighlight.create(prism4j, prism4jTheme)
    }

    LaunchedEffect(didCopy) {
        if (didCopy) {
            delay(1_500)
            didCopy = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, chrome.subtleBorder),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 8.dp, top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.Text(
                    text = languageLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = chrome.secondaryText,
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable {
                            saveController.copyText(
                                label = "$languageLabel code",
                                text = code,
                            )
                            didCopy = true
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (didCopy) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                        contentDescription = if (didCopy) "Copied code" else "Copy code",
                        tint = chrome.secondaryText,
                        modifier = Modifier.size(14.dp),
                    )
                    androidx.compose.material3.Text(
                        text = if (didCopy) "Copied" else "Copy",
                        style = MaterialTheme.typography.labelMedium,
                        color = chrome.secondaryText,
                    )
                }
            }

            if (shouldRenderMarkdownCodeBlockAsDiff(language)) {
                ConversationCleanDiffCodeBlock(
                    code = code,
                    modifier = Modifier.fillMaxWidth(),
                    style = textStyle,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp,
                        vertical = 8.dp,
                    ),
                    enablesSelection = enablesSelection,
                    onLongPress = onLongPress,
                )
            } else {
                val highlightedCode = remember(code, normalizedLanguage, syntaxHighlight) {
                    syntaxHighlight.highlight(normalizedLanguage, code)
                }
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    factory = { viewContext ->
                        TextView(viewContext).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                            )
                            includeFontPadding = false
                            setLineSpacing(0f, 1f)
                            setPadding(0, 0, 0, 0)
                            setBackgroundColor(AndroidColor.TRANSPARENT)
                            background = null
                            typeface = Typeface.MONOSPACE
                            highlightColor = if (enablesSelection) {
                                selectionHighlightColor
                            } else {
                                AndroidColor.TRANSPARENT
                            }
                            linksClickable = false
                            isFocusable = false
                            isClickable = false
                            isLongClickable = enablesSelection || onLongPress != null
                        }
                    },
                    update = { textView ->
                        val previousToken = textView.tag as? ConversationMarkdownCodeBlockRenderToken
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
                                    onLongPress(
                                        IntOffset(
                                            x = lastTouchOffset[0],
                                            y = lastTouchOffset[1],
                                        ),
                                    )
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
                            textView.typeface = Typeface.MONOSPACE
                            textView.setTextColor(renderToken.textColorArgb)
                            renderToken.textSizePx?.let { resolvedTextSizePx ->
                                textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, resolvedTextSizePx)
                            }
                            textView.setLineSpacing(renderToken.lineHeightExtra, 1f)
                            textView.setTextIsSelectable(renderToken.enablesSelection)
                            textView.movementMethod = null
                            textView.setBackgroundColor(AndroidColor.TRANSPARENT)
                            textView.background = null
                            textView.text = highlightedCode
                            textView.tag = renderToken
                        }
                    },
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ConversationMermaidSegment(
    source: String,
    saveController: ConversationMarkdownSaveController,
    onLongPress: ((IntOffset) -> Unit)?,
    onPreview: () -> Unit,
) {
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    val mermaidBridge = remember(source) { MermaidWebViewBridge() }
    var webView by remember(source) { mutableStateOf<WebView?>(null) }
    var isSaving by remember(source) { mutableStateOf(false) }
    var didCopy by remember(source) { mutableStateOf(false) }

    LaunchedEffect(didCopy) {
        if (didCopy) {
            delay(1_500)
            didCopy = false
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
    ) {
        ConversationMermaidPreviewCard(
            source = source,
            modifier = Modifier.fillMaxWidth(),
            minHeight = 120.dp,
            bridge = mermaidBridge,
            onWebViewChanged = { webView = it },
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

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ConversationMarkdownFloatingActionButton(
                contentDescription = if (didCopy) "Copied diagram code" else "Copy diagram code",
                icon = Icons.Outlined.ContentCopy,
                activeIcon = Icons.Outlined.Check,
                isActive = didCopy,
                onClick = {
                    saveController.copyText(
                        label = "Mermaid diagram",
                        text = fencedMermaidCodeBlock(source),
                    )
                    saveController.showMessage("Copied diagram code.")
                    didCopy = true
                },
                buttonSize = 34.dp,
            )
            ConversationMarkdownFloatingActionButton(
                contentDescription = "Save diagram",
                icon = Icons.Outlined.FileDownload,
                isLoading = isSaving,
                onClick = {
                    if (isSaving) {
                        return@ConversationMarkdownFloatingActionButton
                    }
                    val resolvedWebView = webView
                    if (resolvedWebView == null) {
                        saveController.showMessage("Diagram isn't ready to save yet.")
                        return@ConversationMarkdownFloatingActionButton
                    }
                    coroutineScope.launch {
                        isSaving = true
                        try {
                            val bitmap = exportMermaidBitmapFromWebView(
                                webView = resolvedWebView,
                                bridge = mermaidBridge,
                            )
                            if (bitmap != null) {
                                saveController.saveBitmap(
                                    bitmap = bitmap,
                                    suggestedName = "diagram",
                                    onComplete = {
                                        isSaving = false
                                    },
                                )
                            } else {
                                saveController.showMessage("Diagram isn't ready to save yet.")
                                isSaving = false
                            }
                        } catch (error: CancellationException) {
                            isSaving = false
                            throw error
                        } catch (error: Throwable) {
                            isSaving = false
                        }
                    }
                },
                buttonSize = 34.dp,
            )
        }
    }
}

@Composable
private fun ConversationMarkdownImageSegment(
    url: String,
    altText: String?,
    title: String?,
    saveController: ConversationMarkdownSaveController,
    onLongPress: ((IntOffset) -> Unit)?,
    onPreview: () -> Unit,
) {
    val view = LocalView.current
    val chrome = remodexConversationChrome()
    val suggestedName = title ?: altText ?: "image"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp),
    ) {
        AsyncImage(
            model = url,
            contentDescription = altText ?: title ?: "Image",
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
            contentScale = ContentScale.Fit,
        )

        ConversationMarkdownFloatingActionButton(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            contentDescription = "Save image",
            icon = Icons.Outlined.FileDownload,
            onClick = {
                saveController.saveImageFromUrl(
                    imageUrl = url,
                    suggestedName = suggestedName,
                )
            },
        )
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

private fun fencedMermaidCodeBlock(source: String): String {
    val normalizedSource = source.trimEnd('\n', '\r')
    return "```mermaid\n$normalizedSource\n```"
}

@Composable
private fun ConversationMarkdownPreviewDialog(
    preview: ConversationMarkdownPreview,
    saveController: ConversationMarkdownSaveController,
    onDismiss: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    var registeredSaveState by remember(preview) { mutableStateOf<ConversationMarkdownPreviewSaveState?>(null) }
    var didCopyDiagramCode by remember(preview) { mutableStateOf(false) }

    LaunchedEffect(didCopyDiagramCode) {
        if (didCopyDiagramCode) {
            delay(1_500)
            didCopyDiagramCode = false
        }
    }

    val previewSaveState = when (preview) {
        is ConversationMarkdownPreview.Image -> {
            ConversationMarkdownPreviewSaveState(
                onSave = {
                    saveController.saveImageFromUrl(
                        imageUrl = preview.url,
                        suggestedName = preview.title ?: preview.altText ?: "image",
                    )
                },
                isLoading = false,
            )
        }

        is ConversationMarkdownPreview.Mermaid -> registeredSaveState
    }
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
                    saveController = saveController,
                    onSaveStateChanged = { registeredSaveState = it },
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

                if (preview is ConversationMarkdownPreview.Mermaid) {
                    ConversationMarkdownPreviewGlassChip(
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        ConversationMarkdownPreviewToolbarActionButton(
                            onClick = {
                                saveController.copyText(
                                    label = "Mermaid diagram",
                                    text = fencedMermaidCodeBlock(preview.source),
                                )
                                saveController.showMessage("Copied diagram code.")
                                didCopyDiagramCode = true
                            },
                            contentDescription = if (didCopyDiagramCode) {
                                "Copied diagram code"
                            } else {
                                "Copy diagram code"
                            },
                            icon = Icons.Outlined.ContentCopy,
                            activeIcon = Icons.Outlined.Check,
                            isActive = didCopyDiagramCode,
                            tint = colorScheme.onSurface,
                        )
                    }
                }

                previewSaveState?.let { saveState ->
                    ConversationMarkdownPreviewGlassChip(
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        ConversationMarkdownPreviewToolbarActionButton(
                            onClick = saveState.onSave,
                            contentDescription = when (preview) {
                                is ConversationMarkdownPreview.Image -> "Save image"
                                is ConversationMarkdownPreview.Mermaid -> "Save diagram"
                            },
                            icon = Icons.Outlined.FileDownload,
                            isLoading = saveState.isLoading,
                            tint = colorScheme.onSurface,
                        )
                    }
                }
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
    saveController: ConversationMarkdownSaveController,
    onSaveStateChanged: ((ConversationMarkdownPreviewSaveState?) -> Unit),
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val mermaidBridge = remember(preview.source) { MermaidWebViewBridge() }
    var webView by remember(preview.source) { mutableStateOf<WebView?>(null) }
    var isSaving by remember(preview.source) { mutableStateOf(false) }

    LaunchedEffect(webView, preview.source, saveController, coroutineScope, isSaving) {
        onSaveStateChanged(
            webView?.let { resolvedWebView ->
                ConversationMarkdownPreviewSaveState(
                    onSave = save@{
                        if (isSaving) {
                            return@save
                        }
                        coroutineScope.launch {
                            isSaving = true
                            try {
                                val bitmap = exportMermaidBitmapFromWebView(
                                    webView = resolvedWebView,
                                    bridge = mermaidBridge,
                                )
                                if (bitmap != null) {
                                    saveController.saveBitmap(
                                        bitmap = bitmap,
                                        suggestedName = "diagram",
                                        onComplete = {
                                            isSaving = false
                                        },
                                    )
                                } else {
                                    saveController.showMessage("Diagram isn't ready to save yet.")
                                    isSaving = false
                                }
                            } catch (error: CancellationException) {
                                isSaving = false
                                throw error
                            } catch (error: Throwable) {
                                isSaving = false
                            }
                        }
                    },
                    isLoading = isSaving,
                )
            },
        )
    }
    DisposableEffect(onSaveStateChanged) {
        onDispose {
            onSaveStateChanged(null)
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        ConversationMermaidPreviewCard(
            source = preview.source,
            modifier = Modifier.fillMaxSize(),
            previewMode = true,
            bridge = mermaidBridge,
            onWebViewChanged = { webView = it },
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
private fun ConversationMarkdownFloatingActionButton(
    modifier: Modifier = Modifier,
    contentDescription: String,
    icon: ImageVector,
    onClick: () -> Unit,
    activeIcon: ImageVector? = null,
    isActive: Boolean = false,
    buttonSize: androidx.compose.ui.unit.Dp = 40.dp,
    isLoading: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = Color.Black.copy(alpha = 0.48f),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        IconButton(
            onClick = onClick,
            enabled = !isLoading,
            modifier = Modifier.size(buttonSize),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size((buttonSize.value * 0.42f).dp),
                    strokeWidth = 2.dp,
                    color = Color.White.copy(alpha = 0.96f),
                )
            } else {
                Icon(
                    imageVector = if (isActive) {
                        activeIcon ?: icon
                    } else {
                        icon
                    },
                    contentDescription = contentDescription,
                    modifier = Modifier.size((buttonSize.value * 0.45f).dp),
                    tint = Color.White.copy(alpha = 0.96f),
                )
            }
        }
    }
}

@Composable
private fun ConversationMarkdownPreviewToolbarActionButton(
    onClick: () -> Unit,
    contentDescription: String,
    icon: ImageVector,
    tint: Color,
    activeIcon: ImageVector? = null,
    isActive: Boolean = false,
    isLoading: Boolean = false,
) {
    IconButton(
        onClick = onClick,
        enabled = !isLoading,
        modifier = Modifier.size(40.dp),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = tint,
            )
        } else {
            Icon(
                imageVector = if (isActive) {
                    activeIcon ?: icon
                } else {
                    icon
                },
                contentDescription = contentDescription,
                tint = tint,
            )
        }
    }
}

@Composable
private fun ConversationMermaidPreviewCard(
    source: String,
    modifier: Modifier = Modifier,
    previewMode: Boolean = false,
    minHeight: androidx.compose.ui.unit.Dp = 220.dp,
    bridge: MermaidWebViewBridge,
    onWebViewChanged: (WebView?) -> Unit = {},
) {
    val chrome = remodexConversationChrome()
    val isDark = isSystemInDarkTheme()
    val density = LocalDensity.current
    var contentHeightPx by remember(source) { mutableStateOf(0) }
    val resolvedHeight = remember(contentHeightPx, density) {
        if (contentHeightPx <= 0) {
            minHeight
        } else {
            with(density) { contentHeightPx.toDp() }.coerceAtLeast(minHeight)
        }
    }
    DisposableEffect(bridge) {
        bridge.onHeight = { reportedHeight ->
            contentHeightPx = reportedHeight
        }
        onDispose {
            bridge.onHeight = null
            bridge.onExportDataUrl = null
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
    }

    val webContent: @Composable () -> Unit = {
        DisposableEffect(onWebViewChanged) {
            onDispose {
                onWebViewChanged(null)
            }
        }
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
                    addJavascriptInterface(bridge, "AndroidMermaid")
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {}
                    onWebViewChanged(this)
                }
            },
            update = { webView ->
                onWebViewChanged(webView)
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
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)),
        ) {
            webContent()
        }
    }
}

private suspend fun exportMermaidBitmapFromWebView(
    webView: WebView,
    bridge: MermaidWebViewBridge,
): Bitmap? {
    val dataUrl = requestMermaidPngDataUrl(
        webView = webView,
        bridge = bridge,
        exportScale = 4,
    ) ?: return null
    val pngBytes = decodeInlineImageDataUrlBytes(dataUrl) ?: return null
    return BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.size)
}

private suspend fun requestMermaidPngDataUrl(
    webView: WebView,
    bridge: MermaidWebViewBridge,
    exportScale: Int,
): String? =
    suspendCancellableCoroutine { continuation ->
        bridge.onExportDataUrl = { dataUrl ->
            if (continuation.isActive) {
                bridge.onExportDataUrl = null
                continuation.resume(dataUrl)
            }
        }
        webView.evaluateJavascript(
            """
            (function() {
              const bridge = window.AndroidMermaid;
              if (!bridge || typeof bridge.postExportDataUrl !== 'function') {
                return;
              }

              const tryExport = function(attempt) {
                try {
                  const root = document.getElementById('root') || document.body;
                  const svg = root ? root.querySelector('svg') : null;
                  if (!svg) {
                    if (attempt < 20) {
                      window.setTimeout(function() { tryExport(attempt + 1); }, 120);
                    } else {
                      bridge.postExportDataUrl(null);
                    }
                    return;
                  }

                  const rect = svg.getBoundingClientRect();
                  const viewBox = svg.viewBox && svg.viewBox.baseVal;
                  const intrinsicWidth =
                    (viewBox && viewBox.width) ||
                    parseFloat(svg.getAttribute('width')) ||
                    rect.width ||
                    0;
                  const intrinsicHeight =
                    (viewBox && viewBox.height) ||
                    parseFloat(svg.getAttribute('height')) ||
                    rect.height ||
                    0;
                  const width = Math.max(
                    1,
                    Math.ceil(intrinsicWidth)
                  );
                  const height = Math.max(
                    1,
                    Math.ceil(intrinsicHeight)
                  );
                  const resolvedScale = Math.max(
                    1,
                    $exportScale,
                    Math.ceil(window.devicePixelRatio || 1)
                  );
                  const clonedSvg = svg.cloneNode(true);
                  clonedSvg.setAttribute('xmlns', 'http://www.w3.org/2000/svg');
                  clonedSvg.setAttribute('xmlns:xlink', 'http://www.w3.org/1999/xlink');
                  if (!clonedSvg.getAttribute('viewBox') && viewBox && viewBox.width > 0 && viewBox.height > 0) {
                    clonedSvg.setAttribute('viewBox', `${'$'}{viewBox.x} ${'$'}{viewBox.y} ${'$'}{viewBox.width} ${'$'}{viewBox.height}`);
                  }
                  if (!clonedSvg.getAttribute('width')) {
                    clonedSvg.setAttribute('width', String(width));
                  }
                  if (!clonedSvg.getAttribute('height')) {
                    clonedSvg.setAttribute('height', String(height));
                  }
                  const serializedSvg = new XMLSerializer().serializeToString(clonedSvg);
                  const encodedSvg = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(serializedSvg);
                  const image = new Image();
                  image.onload = function() {
                    try {
                      const canvas = document.createElement('canvas');
                      canvas.width = width * resolvedScale;
                      canvas.height = height * resolvedScale;
                      const context = canvas.getContext('2d');
                      if (!context) {
                        bridge.postExportDataUrl(null);
                        return;
                      }
                      context.clearRect(0, 0, canvas.width, canvas.height);
                      context.scale(resolvedScale, resolvedScale);
                      context.drawImage(image, 0, 0, width, height);
                      bridge.postExportDataUrl(canvas.toDataURL('image/png'));
                    } catch (error) {
                      bridge.postExportDataUrl(null);
                    }
                  };
                  image.onerror = function() {
                    if (attempt < 20) {
                      window.setTimeout(function() { tryExport(attempt + 1); }, 120);
                    } else {
                      bridge.postExportDataUrl(null);
                    }
                  };
                  image.src = encodedSvg;
                } catch (error) {
                  if (attempt < 20) {
                    window.setTimeout(function() { tryExport(attempt + 1); }, 120);
                  } else {
                    bridge.postExportDataUrl(null);
                  }
                }
              };

              tryExport(0);
            })();
            """.trimIndent(),
            null,
        )
        continuation.invokeOnCancellation {
            bridge.onExportDataUrl = null
        }
    }

private suspend fun loadBitmapForSave(
    context: Context,
    imageUrl: String,
): Bitmap? = withContext(Dispatchers.IO) {
    val request = ImageRequest.Builder(context)
        .data(imageUrl)
        .allowHardware(false)
        .build()
    val result = context.imageLoader.execute(request) as? SuccessResult ?: return@withContext null
    result.drawable.toBitmap(config = Bitmap.Config.ARGB_8888)
}

private fun buildMermaidHtml(
    source: String,
    isDark: Boolean,
    accentColor: Int,
    textColor: Int,
    borderColor: Int,
    surfaceColor: Int,
    previewMode: Boolean = false,
    exportMode: Boolean = false,
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
                overflow: ${if (previewMode && !exportMode) "auto" else "hidden"};
                width: 100%;
                height: 100%;
              }
              body {
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                ${if (previewMode && !exportMode) "display: flex; align-items: center; justify-content: center;" else ""}
              }
              #root {
                box-sizing: border-box;
                width: auto;
                min-height: auto;
                padding: 0;
                border-radius: 0;
                background: transparent;
                border: none;
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
                max-width: ${if (previewMode || exportMode) "none" else "100%"};
                max-height: ${if (previewMode || exportMode) "none" else "auto"};
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
                    if (window.AndroidMermaid && window.AndroidMermaid.postHeight) {
                      window.AndroidMermaid.postHeight(height);
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

private class MermaidWebViewBridge {
    private val mainHandler = Handler(Looper.getMainLooper())

    var onHeight: ((Int) -> Unit)? = null
    var onExportDataUrl: ((String?) -> Unit)? = null

    @JavascriptInterface
    fun postHeight(heightPx: Int) {
        if (heightPx <= 0) {
            return
        }
        mainHandler.post {
            onHeight?.invoke(heightPx)
        }
    }

    @JavascriptInterface
    fun postExportDataUrl(dataUrl: String?) {
        mainHandler.post {
            onExportDataUrl?.invoke(dataUrl)
        }
    }
}
