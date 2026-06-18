package com.pnu.orbit.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.pnu.orbit.domain.model.PhotoTag
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.Locale

/**
 * On-device travel-scene classifier backed by a TensorFlow Lite model trained in
 * Teachable Machine. Maps each photo to a [PhotoTag].
 *
 * Per CLAUDE.md ("ML must never block photo import or crash"), this never throws:
 * a missing/corrupt model, an undecodable image, or a low-confidence prediction all
 * resolve to [PhotoTag.UNKNOWN]. Until `assets/ml/photo_classifier.tflite` exists the
 * classifier simply behaves like the [FallbackPhotoClassifier].
 *
 * Expects a Teachable Machine "floating point" image model: 224x224x3 input normalized
 * to [-1, 1], softmax probabilities out. Train classes named exactly after [PhotoTag]
 * values (CITY, SEA, MOUNTAIN, FOOD, NIGHT, LANDMARK) and drop the export here:
 *  - assets/ml/photo_classifier.tflite
 *  - assets/ml/photo_labels.txt  (one label per line, in training order)
 *
 * Image preprocessing is done by hand (no tensorflow-lite-support, which has a manifest
 * namespace clash under AGP 9).
 */
class TfLitePhotoClassifier(
    private val context: Context,
) : PhotoClassifier {

    // Loaded lazily on first classify() (already invoked on Dispatchers.IO) and reused
    // for the whole session. null == model unavailable -> degrade to UNKNOWN.
    private val interpreter: Interpreter? by lazy { runCatching { loadInterpreter() }.getOrNull() }
    private val labels: List<PhotoTag> by lazy { runCatching { loadLabels() }.getOrDefault(emptyList()) }

    override suspend fun classify(uri: Uri): PhotoTag {
        val tflite = interpreter ?: return PhotoTag.UNKNOWN
        if (labels.isEmpty()) return PhotoTag.UNKNOWN
        val bitmap = decodeBitmap(uri) ?: return PhotoTag.UNKNOWN

        return runCatching {
            val input = toInputBuffer(bitmap)
            val output = Array(1) { FloatArray(labels.size) }
            tflite.run(input, output)

            val scores = output[0]
            val bestIndex = scores.indices.maxByOrNull { scores[it] } ?: return@runCatching PhotoTag.UNKNOWN
            if (scores[bestIndex] < CONFIDENCE_THRESHOLD) {
                PhotoTag.UNKNOWN
            } else {
                labels.getOrElse(bestIndex) { PhotoTag.UNKNOWN }
            }
        }.getOrDefault(PhotoTag.UNKNOWN)
    }

    private fun decodeBitmap(uri: Uri): Bitmap? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        }
    }.getOrNull()

    /** Resize to INPUT_SIZE and pack RGB floats normalized to [-1, 1]. */
    private fun toInputBuffer(bitmap: Bitmap): ByteBuffer {
        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val buffer = ByteBuffer
            .allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * CHANNELS)
            .order(ByteOrder.nativeOrder())
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            buffer.putFloat((r - IMAGE_MEAN) / IMAGE_STD)
            buffer.putFloat((g - IMAGE_MEAN) / IMAGE_STD)
            buffer.putFloat((b - IMAGE_MEAN) / IMAGE_STD)
        }
        buffer.rewind()
        return buffer
    }

    private fun loadInterpreter(): Interpreter {
        val assetFd = context.assets.openFd(MODEL_ASSET)
        val model: MappedByteBuffer = FileInputStream(assetFd.fileDescriptor).use { input ->
            input.channel.map(
                FileChannel.MapMode.READ_ONLY,
                assetFd.startOffset,
                assetFd.declaredLength,
            )
        }
        return Interpreter(model, Interpreter.Options())
    }

    private fun loadLabels(): List<PhotoTag> =
        context.assets.open(LABELS_ASSET).bufferedReader().useLines { lines ->
            lines.map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { line ->
                    // Teachable Machine writes "0 CITY"; tolerate a plain "CITY" too.
                    val token = line.substringAfter(' ', line).trim().uppercase(Locale.US)
                    runCatching { PhotoTag.valueOf(token) }.getOrDefault(PhotoTag.UNKNOWN)
                }
                .toList()
        }

    companion object {
        private const val MODEL_ASSET = "ml/photo_classifier.tflite"
        private const val LABELS_ASSET = "ml/photo_labels.txt"
        private const val INPUT_SIZE = 224
        private const val CHANNELS = 3
        // Teachable Machine MobileNet base: normalize [0,255] -> [-1,1].
        private const val IMAGE_MEAN = 127.5f
        private const val IMAGE_STD = 127.5f
        private const val CONFIDENCE_THRESHOLD = 0.6f
    }
}
