package com.emanueledipietro.remodex.platform.media

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object GalleryImageSaver {
    private const val albumName = "Remodex"
    private const val mimeType = "image/png"

    fun requiredWritePermission(sdkInt: Int = Build.VERSION.SDK_INT): String? =
        if (sdkInt < Build.VERSION_CODES.Q) {
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        } else {
            null
        }

    suspend fun saveBitmap(
        context: Context,
        bitmap: Bitmap,
        suggestedName: String,
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveScopedBitmap(context, bitmap, suggestedName)
            } else {
                saveLegacyBitmap(context, bitmap, suggestedName)
            }
        }
    }

    internal fun buildDisplayName(
        suggestedName: String,
        timestampMs: Long = System.currentTimeMillis(),
    ): String {
        val sanitizedBaseName = sanitizeDisplayName(suggestedName)
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(timestampMs))
        return "$sanitizedBaseName-$timestamp.png"
    }

    internal fun sanitizeDisplayName(raw: String): String {
        val sanitized = raw
            .trim()
            .replace(Regex("[^A-Za-z0-9._-]+"), "-")
            .replace(Regex("-{2,}"), "-")
            .trim('-', '.')
            .take(48)
        return sanitized.ifBlank { "image" }
    }

    private fun saveScopedBitmap(
        context: Context,
        bitmap: Bitmap,
        suggestedName: String,
    ): Uri {
        val displayName = buildDisplayName(suggestedName)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.WIDTH, bitmap.width)
            put(MediaStore.Images.Media.HEIGHT, bitmap.height)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$albumName")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values)
            ?: throw IOException("Could not create a gallery entry.")

        try {
            resolver.openOutputStream(uri)?.use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    throw IOException("Could not encode the image.")
                }
            } ?: throw IOException("Could not open the gallery entry for writing.")

            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    private fun saveLegacyBitmap(
        context: Context,
        bitmap: Bitmap,
        suggestedName: String,
    ): Uri {
        val picturesDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val albumDirectory = File(picturesDirectory, albumName)
        if (!albumDirectory.exists() && !albumDirectory.mkdirs()) {
            throw IOException("Could not create the gallery folder.")
        }

        val outputFile = File(albumDirectory, buildDisplayName(suggestedName))
        FileOutputStream(outputFile).use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw IOException("Could not encode the image.")
            }
            output.flush()
        }

        MediaScannerConnection.scanFile(
            context,
            arrayOf(outputFile.absolutePath),
            arrayOf(mimeType),
            null,
        )
        return Uri.fromFile(outputFile)
    }
}
