package com.drfxai.maximusvpn.ui.qr

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.drfxai.maximusvpn.ui.theme.AppTheme
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

/**
 * Live-camera QR scanner for share-link import.
 *
 * CameraX feeds YUV frames into ZXing's MultiFormatReader; decoding happens on a
 * dedicated single thread and stops at the first successful hit. Gallery import is
 * handled by the caller via [QrImageDecoder] (bitmap path).
 */
@Composable
fun QrScannerScreen(
    onDecoded: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val colors = AppTheme.colors

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Gallery pick — decode via QrImageDecoder off the UI thread.
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val decoded = QrImageDecoder.decodeUri(context, uri)
            if (decoded != null) onDecoded(decoded) else onClose()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Scan QR Code", fontSize = 20.sp, fontWeight = MaterialTheme.typography.titleLarge.fontWeight, color = colors.textPrimary)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.textSecondary)
            }
        }
        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(20.dp))
                .border(2.dp, colors.primary, RoundedCornerShape(20.dp))
                .background(colors.consoleBackground)
        ) {
            if (hasPermission) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        startCamera(ctx, previewView, lifecycleOwner, onDecoded)
                        previewView
                    }
                )
            } else {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Camera permission required", color = colors.textSecondary, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                    ) { Text("Grant", color = colors.onPrimary) }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        OutlinedButton(
            onClick = { galleryLauncher.launch("image/*") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = colors.primary)
            Spacer(Modifier.width(6.dp))
            Text("Pick from gallery", color = colors.textPrimary)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Point the camera at a vless:// / vmess:// / ss:// / trojan:// / hysteria2:// QR code",
            color = colors.textMuted,
            fontSize = 12.sp
        )
    }
}

private fun startCamera(
    context: android.content.Context,
    previewView: PreviewView,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    onDecoded: (String) -> Unit
) {
    val future = ProcessCameraProvider.getInstance(context)
    val executor = ContextCompat.getMainExecutor(context)
    future.addListener({
        try {
            val provider = future.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val reader = MultiFormatReader().apply {
                setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
            }
            val analysisExecutor = Executors.newSingleThreadExecutor()
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            var delivered = false
            analysis.setAnalyzer(analysisExecutor) { proxy ->
                if (delivered) { proxy.close(); return@setAnalyzer }
                val text = decodeFrame(reader, proxy)
                if (text != null && !delivered) {
                    delivered = true
                    executor.execute { onDecoded(text) }
                }
                proxy.close()
            }

            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis
            )
        } catch (_: Exception) {
            // Camera unavailable (emulator without webcam, permission race...) — UI shows hint text.
        }
    }, executor)
}

/** YUV → luminance → ZXing. Returns the payload string or null for this frame. */
private fun decodeFrame(reader: MultiFormatReader, proxy: ImageProxy): String? {
    return try {
        val plane = proxy.planes[0]
        val buffer = plane.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val source = PlanarYUVLuminanceSource(
            bytes, plane.rowStride, proxy.height,
            0, 0, proxy.width.coerceAtMost(plane.rowStride), proxy.height, false
        )
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        val result = reader.decodeWithState(bitmap)
        reader.reset()
        result.text
    } catch (_: Exception) {
        try { reader.reset() } catch (_: Exception) {}
        null
    } finally {
        proxy.close()
    }
}
