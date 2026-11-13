package com.snipnotes.app

import android.os.SystemClock
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors

/**
 * Live camera preview.
 * - Pass an [analyzer] for the price scanner (live text reading).
 * - Leave it null for photo mode and receive the [ImageCapture] through [onCapture].
 */
@Composable
fun CameraView(
    modifier: Modifier = Modifier,
    analyzer: ImageAnalysis.Analyzer? = null,
    onCapture: (ImageCapture) -> Unit = {},
) {
    val context = LocalContext.current
    val owner = context as LifecycleOwner
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val scanning = analyzer != null

    DisposableEffect(scanning) {
        val executor = Executors.newSingleThreadExecutor()
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false

        future.addListener({
            if (disposed) return@addListener
            val p = future.get()
            provider = p
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)
            p.unbindAll()
            try {
                if (analyzer != null) {
                    val selector = ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                Size(1280, 720),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                            )
                        )
                        .build()
                    val analysis = ImageAnalysis.Builder()
                        .setResolutionSelector(selector)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(executor, analyzer)
                    p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                } else {
                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                    onCapture(capture)
                }
            } catch (_: Exception) {
                // Camera unavailable: the preview simply stays blank.
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed = true
            provider?.unbindAll()
            executor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

/** Reads text from camera frames about three times a second, fully on the device. */
class TextAnalyzer<T : Any>(
    private val parse: (Text) -> T?,
    private val onResult: (T?) -> Unit,
) : ImageAnalysis.Analyzer {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var last = 0L

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(proxy: ImageProxy) {
        val media = proxy.image
        val now = SystemClock.elapsedRealtime()
        if (media == null || now - last < 330) {
            proxy.close()
            return
        }
        last = now
        val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        recognizer.process(image)
            .addOnSuccessListener { onResult(parse(it)) }
            .addOnFailureListener { onResult(null) }
            .addOnCompleteListener { proxy.close() }
    }
}
