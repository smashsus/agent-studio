package com.example.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

class OcrHelper(private val context: Context) {

    suspend fun recognizeBitmap(bitmap: Bitmap): Result<String> = withContext(Dispatchers.Default) {
        runCatching {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val image = InputImage.fromBitmap(bitmap, 0)
            suspendCancellableCoroutine { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        if (cont.isActive) cont.resume(Result.success(result.text.trim()))
                        runCatching { recognizer.close() }
                    }
                    .addOnFailureListener { err ->
                        if (cont.isActive) cont.resume(Result.failure(err))
                        runCatching { recognizer.close() }
                    }
                    .addOnCanceledListener {
                        if (cont.isActive) cont.resume(Result.failure(IllegalStateException("OCR cancelled.")))
                        runCatching { recognizer.close() }
                    }
            }
        }.getOrElse { Result.failure(it) }
    }

    suspend fun recognizeImageFile(file: File): Result<String> = withContext(Dispatchers.IO) {
        if (!file.exists() || !file.isFile) {
            return@withContext Result.failure(IllegalArgumentException("Image file not found: ${file.name}"))
        }
        val bitmap = decodeDownsampledBitmapFromFile(file)
            ?: return@withContext Result.failure(IllegalArgumentException("Unable to decode image '${file.name}'."))
        try {
            recognizeBitmap(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun recognizeImageUri(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        val sampleSize = computeSampleSize(bounds.outWidth, bounds.outHeight, 2048)
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, opts)
        } ?: return@withContext Result.failure(IllegalArgumentException("Unable to open image URI."))
        try {
            recognizeBitmap(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    private fun decodeDownsampledBitmapFromFile(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = computeSampleSize(bounds.outWidth, bounds.outHeight, 2048)
        }
        return BitmapFactory.decodeFile(file.absolutePath, opts)
    }

    private fun computeSampleSize(width: Int, height: Int, maxDim: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        var w = width
        var h = height
        while (w > maxDim || h > maxDim) {
            sample *= 2
            w /= 2
            h /= 2
        }
        return sample
    }
}
