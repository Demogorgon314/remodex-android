package com.emanueledipietro.remodex.platform.media

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.emanueledipietro.remodex.model.RemodexComposerAttachment
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.Locale
import kotlin.math.roundToInt

data class ComposerCameraCapture(
    val file: File,
    val uri: Uri,
)

data class ComposerAttachmentResolution(
    val attachments: List<RemodexComposerAttachment>,
    val failedCount: Int,
)

fun resolveComposerAttachments(
    context: Context,
    uris: List<Uri>,
): List<RemodexComposerAttachment> {
    return resolveComposerAttachmentResolution(context, uris).attachments
}

fun resolveComposerAttachmentResolution(
    context: Context,
    uris: List<Uri>,
): ComposerAttachmentResolution {
    val attachments = uris.mapNotNull { uri ->
        context.resolveComposerAttachment(uri)
    }
    return ComposerAttachmentResolution(
        attachments = attachments,
        failedCount = (uris.size - attachments.size).coerceAtLeast(0),
    )
}

fun canLaunchComposerCameraCapture(context: Context): Boolean {
    if (!context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY)) {
        return false
    }
    val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
    return captureIntent.resolveActivity(context.packageManager) != null
}

fun createComposerCameraCapture(context: Context): ComposerCameraCapture? {
    return runCatching {
        val captureDirectory = File(context.cacheDir, ComposerCameraCaptureDirectoryName).apply {
            mkdirs()
        }
        val captureFile = File.createTempFile(
            ComposerCameraCaptureFilePrefix,
            ComposerCameraCaptureFileSuffix,
            captureDirectory,
        )
        val captureUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            captureFile,
        )
        ComposerCameraCapture(file = captureFile, uri = captureUri)
    }.getOrNull()
}

private fun Context.resolveDisplayName(uri: Uri): String {
    return contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (displayNameIndex >= 0 && cursor.moveToFirst()) {
            cursor.getString(displayNameIndex)
        } else {
            null
        }
    } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Selected image"
}

fun Context.isSupportedComposerImageUri(uri: Uri): Boolean {
    val uriValue = uri.toString()
    if (uriValue.startsWith("data:image", ignoreCase = true)) {
        return true
    }

    val mimeType = contentResolver.getType(uri)?.trim()
    if (!mimeType.isNullOrEmpty() && mimeType.startsWith("image/", ignoreCase = true)) {
        return true
    }

    val extension = resolveDisplayName(uri)
        .substringAfterLast('.', "")
        .lowercase(Locale.ROOT)
    return extension in SupportedComposerImageExtensions
}

private fun Context.resolveComposerAttachment(uri: Uri): RemodexComposerAttachment? {
    val uriString = uri.toString()
    val payloadDataUrl = resolvePayloadDataUrl(uri) ?: return null
    return RemodexComposerAttachment(
        id = uriString,
        uriString = uriString,
        displayName = resolveDisplayName(uri),
        payloadDataUrl = payloadDataUrl,
    )
}

private fun Context.resolvePayloadDataUrl(uri: Uri): String? {
    val imageBytes = contentResolver.openInputStream(uri)?.use { inputStream ->
        inputStream.readBytes()
    } ?: return null

    val normalizedPayload = normalizePayloadJpeg(imageBytes)
    if (normalizedPayload != null) {
        return "data:image/jpeg;base64,${Base64.getEncoder().encodeToString(normalizedPayload)}"
    }

    val contentType = contentResolver.getType(uri)?.trim()?.takeIf { it.startsWith("image/") } ?: return null
    return "data:$contentType;base64,${Base64.getEncoder().encodeToString(imageBytes)}"
}

private fun normalizePayloadJpeg(sourceBytes: ByteArray): ByteArray? {
    val sourceBitmap = BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size) ?: return null
    val sourceWidth = sourceBitmap.width
    val sourceHeight = sourceBitmap.height
    if (sourceWidth <= 0 || sourceHeight <= 0) {
        return null
    }

    val longestSide = maxOf(sourceWidth, sourceHeight)
    val scale = minOf(1f, MaxPayloadDimension.toFloat() / longestSide.toFloat())
    val renderedBitmap = if (scale < 1f) {
        Bitmap.createScaledBitmap(
            sourceBitmap,
            maxOf(1, (sourceWidth * scale).roundToInt()),
            maxOf(1, (sourceHeight * scale).roundToInt()),
            true,
        )
    } else {
        sourceBitmap
    }

    return ByteArrayOutputStream().use { outputStream ->
        if (!renderedBitmap.compress(Bitmap.CompressFormat.JPEG, PayloadCompressionQuality, outputStream)) {
            return null
        }
        outputStream.toByteArray()
    }
}

private const val MaxPayloadDimension = 1600
private const val PayloadCompressionQuality = 80
private const val ComposerCameraCaptureDirectoryName = "composer-captures"
private const val ComposerCameraCaptureFilePrefix = "composer-capture-"
private const val ComposerCameraCaptureFileSuffix = ".jpg"
private val SupportedComposerImageExtensions = setOf(
    "avif",
    "bmp",
    "gif",
    "heic",
    "heif",
    "jpeg",
    "jpg",
    "png",
    "webp",
)
