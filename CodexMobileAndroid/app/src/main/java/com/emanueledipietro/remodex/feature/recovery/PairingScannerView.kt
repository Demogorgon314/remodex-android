package com.emanueledipietro.remodex.feature.recovery

import android.content.Context
import android.graphics.Rect
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import kotlin.math.max
import kotlin.math.min

@Composable
fun PairingScannerView(
    onScan: (String) -> Unit,
    scanResetCounter: Int = 0,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var barcodeView by remember { mutableStateOf<BarcodeView?>(null) }
    var lastScannedCode by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(scanResetCounter) {
        lastScannedCode = null
    }

    AndroidView(
        modifier = modifier,
        factory = {
            FullPreviewBarcodeView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
                decodeContinuous { result ->
                    val code = result.text ?: return@decodeContinuous
                    if (code == lastScannedCode) {
                        return@decodeContinuous
                    }
                    lastScannedCode = code
                    onScan(code)
                }
                resume()
                barcodeView = this
            }
        },
        update = { view ->
            barcodeView = view
        },
    )

    DisposableEffect(lifecycleOwner, barcodeView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> barcodeView?.resume()
                Lifecycle.Event.ON_PAUSE -> barcodeView?.pause()
                Lifecycle.Event.ON_DESTROY -> barcodeView?.pause()
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            barcodeView?.pause()
        }
    }
}

internal fun calculateFullPreviewFramingRect(
    container: Rect,
    surface: Rect,
): Rect = calculateFullPreviewFramingBounds(
    container = ScannerFrameBounds.fromRect(container),
    surface = ScannerFrameBounds.fromRect(surface),
).toRect()

internal fun calculateFullPreviewFramingBounds(
    container: ScannerFrameBounds,
    surface: ScannerFrameBounds,
): ScannerFrameBounds {
    val left = max(container.left, surface.left)
    val top = max(container.top, surface.top)
    val right = min(container.right, surface.right)
    val bottom = min(container.bottom, surface.bottom)
    return if (left < right && top < bottom) {
        ScannerFrameBounds(left = left, top = top, right = right, bottom = bottom)
    } else {
        ScannerFrameBounds.EMPTY
    }
}

internal data class ScannerFrameBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    fun toRect(): Rect = Rect(left, top, right, bottom)

    companion object {
        val EMPTY = ScannerFrameBounds(left = 0, top = 0, right = 0, bottom = 0)

        fun fromRect(rect: Rect): ScannerFrameBounds = ScannerFrameBounds(
            left = rect.left,
            top = rect.top,
            right = rect.right,
            bottom = rect.bottom,
        )
    }
}

private class FullPreviewBarcodeView(
    context: Context,
) : BarcodeView(context) {
    override fun calculateFramingRect(
        container: Rect,
        surface: Rect,
    ): Rect {
        // Match iOS: scan across the full visible camera preview while keeping the guide box visual-only.
        return calculateFullPreviewFramingRect(container, surface)
    }
}
