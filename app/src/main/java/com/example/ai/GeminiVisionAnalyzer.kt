package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

private const val TAG = "GeminiVisionAnalyzer"

class GeminiVisionAnalyzer {
    private val model by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-3.8-flash"
        )
    }

    suspend fun analyzeImage(
        bitmap: Bitmap,
        question: String,
        ownerName: String
    ): Result<String> {
        return try {
            val scaled = scaleBitmapIfNeeded(bitmap, maxDimension = 1280)
            val prompt = question.trim().ifEmpty {
                "Describe what you see in this photo in a warm, natural, concise voice-friendly style for $ownerName."
            }
            val response = withTimeout(60_000L) {
                model.generateContent(
                    content {
                        image(scaled)
                        text(
                            "You are BRSTI, a witty, warm female personal AI assistant talking to $ownerName. " +
                                "Respond in the same language as the question (Bengali, English, or Hindi; default to warm Bengali if no language specified). " +
                                "Question: $prompt"
                        )
                    }
                )
            }
            val text = response.text?.trim()
            if (!text.isNullOrBlank()) {
                Result.success(text)
            } else {
                Result.failure(IllegalStateException("Gemini returned an empty visual description."))
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Image analysis timed out", e)
            Result.failure(IllegalStateException("Image analysis timed out after 60 seconds. Please try again.", e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Image analysis failed", e)
            Result.failure(e)
        }
    }

    private fun scaleBitmapIfNeeded(source: Bitmap, maxDimension: Int): Bitmap {
        val width = source.width
        val height = source.height
        if (width <= maxDimension && height <= maxDimension) return source
        val ratio = width.toFloat() / height.toFloat()
        val targetW: Int
        val targetH: Int
        if (width >= height) {
            targetW = maxDimension
            targetH = (maxDimension / ratio).toInt().coerceAtLeast(1)
        } else {
            targetH = maxDimension
            targetW = (maxDimension * ratio).toInt().coerceAtLeast(1)
        }
        return Bitmap.createScaledBitmap(source, targetW, targetH, true)
    }
}
