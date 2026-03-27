package com.emanueledipietro.remodex.platform.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.emanueledipietro.remodex.model.RemodexComposerAttachment
import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlin.math.roundToInt

fun resolveComposerAttachments(
    context: Context,
    uris: List<Uri>,
): List<RemodexComposerAttachment> {
    return uris.mapNotNull { uri ->
        val uriString = uri.toString()
        val payloadDataUrl = context.resolvePayloadDataUrl(uri) ?: return@mapNotNull null
        RemodexComposerAttachment(
            id = uriString,
            uriString = uriString,
            displayName = context.resolveDisplayName(uri),
            payloadDataUrl = payloadDataUrl,
        )
    }
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
