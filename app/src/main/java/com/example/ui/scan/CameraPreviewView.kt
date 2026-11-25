package com.example.ui.scan

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.Champagne400
import com.example.ui.theme.EmeraldNeon400
import com.example.ui.theme.Obsidian950
import com.example.ui.theme.PureWhite

private const val CAMERA_TAG = "CameraPreviewView"

@Composable
fun CameraPreviewView(
    onImageCaptured: (Bitmap) -> Unit,
    onCameraError: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var isCapturing by remember { mutableStateOf(false) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    DisposableEffect(lifecycleOwner, lensFacing) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        var boundProvider: ProcessCameraProvider? = null

        val listener = Runnable {
            try {
                val cameraProvider = providerFuture.get()
                boundProvider = cameraProvider
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val selector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Log.e(CAMERA_TAG, "Failed to bind CameraX use cases", e)
                onCameraError(e.localizedMessage ?: "Camera unavailable on this device.")
            }
        }

        providerFuture.addListener(listener, executor)

        onDispose {
            try {
                boundProvider?.unbindAll()
            } catch (_: Exception) {
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(290.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Obsidian950)
            .border(1.dp, Champagne400.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Luxury Precision Corner Reticles
        Canvas(modifier = Modifier.fillMaxSize().padding(22.dp)) {
            val len = 26.dp.toPx()
            val stroke = 2.5.dp.toPx()
            val w = size.width
            val h = size.height
            val color = EmeraldNeon400

            // Top-left
            drawLine(color, Offset(0f, 0f), Offset(len, 0f), stroke, StrokeCap.Round)
            drawLine(color, Offset(0f, 0f), Offset(0f, len), stroke, StrokeCap.Round)
            // Top-right
            drawLine(color, Offset(w, 0f), Offset(w - len, 0f), stroke, StrokeCap.Round)
            drawLine(color, Offset(w, 0f), Offset(w, len), stroke, StrokeCap.Round)
            // Bottom-left
            drawLine(color, Offset(0f, h), Offset(len, h), stroke, StrokeCap.Round)
            drawLine(color, Offset(0f, h), Offset(0f, h - len), stroke, StrokeCap.Round)
            // Bottom-right
            drawLine(color, Offset(w, h), Offset(w - len, h), stroke, StrokeCap.Round)
            drawLine(color, Offset(w, h), Offset(w, h - len), stroke, StrokeCap.Round)
        }

        // Top overlay badge
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.55f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
        ) {
            Text(
                text = "Align embossed text & relief inside frame",
                color = PureWhite,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall
            )
        }

        // Bottom Camera Controls (Cancel, Capture Shutter, Flip)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onCancel,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f))
                    .testTag("camera_cancel_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Cancel camera",
                    tint = PureWhite
                )
            }

            Surface(
                onClick = {
                    if (isCapturing) return@Surface
                    isCapturing = true
                    imageCapture.takePicture(
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                try {
                                    val raw = image.toBitmap()
                                    val rotation = image.imageInfo.rotationDegrees
                                    val rotated = if (rotation != 0) {
                                        val m = Matrix().apply { postRotate(rotation.toFloat()) }
                                        Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true).also {
                                            if (it !== raw) raw.recycle()
                                        }
                                    } else {
                                        raw
                                    }
                                    onImageCaptured(rotated)
                                } catch (e: Exception) {
                                    Log.e(CAMERA_TAG, "Error converting captured frame", e)
                                    onCameraError("Failed to process camera frame.")
                                } finally {
                                    image.close()
                                    isCapturing = false
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                isCapturing = false
                                Log.e(CAMERA_TAG, "Photo capture failed", exception)
                                onCameraError(exception.localizedMessage ?: "Camera capture failed.")
                            }
                        }
                    )
                },
                shape = CircleShape,
                color = Champagne400,
                border = BorderStroke(3.dp, PureWhite),
                modifier = Modifier
                    .size(62.dp)
                    .testTag("camera_shutter_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoCamera,
                        contentDescription = "Capture product photo",
                        tint = Obsidian950,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            IconButton(
                onClick = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f))
                    .testTag("camera_flip_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Cameraswitch,
                    contentDescription = "Switch front or rear camera",
                    tint = PureWhite
                )
            }
        }
    }
}
