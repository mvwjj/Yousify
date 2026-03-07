package com.mvwj.yousify.youtube

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.sqrt

/**
 * ÐšÐ»Ð°ÑÑ Ð´Ð»Ñ Ñ€Ð°Ð±Ð¾Ñ‚Ñ‹ Ñ Ð¼Ð¾Ð´ÐµÐ»ÑŒÑŽ SBERT (paraphrase-multilingual-MiniLM-L12-v2)
 * Ð˜ÑÐ¿Ð¾Ð»ÑŒÐ·ÑƒÐµÑ‚ÑÑ Ð´Ð»Ñ ÑÐµÐ¼Ð°Ð½Ñ‚Ð¸Ñ‡ÐµÑÐºÐ¾Ð³Ð¾ ÑÑ€Ð°Ð²Ð½ÐµÐ½Ð¸Ñ Ñ‚ÐµÐºÑÑ‚Ð¾Ð²
 */
class SbertModel(context: Context) {
    private val interpreter: Interpreter
    private val embedding_size = 384  // Ð Ð°Ð·Ð¼ÐµÑ€ ÑÐ¼Ð±ÐµÐ´Ð´Ð¸Ð½Ð³Ð° Ð´Ð»Ñ MiniLM-L12-v2

    init {
        // Ð—Ð°Ð³Ñ€ÑƒÐ·ÐºÐ° Ð¼Ð¾Ð´ÐµÐ»Ð¸ Ð¸Ð· assets
        val assetManager = context.assets
        val modelFile = assetManager.openFd("sbert_model.tflite")
        val fileChannel = FileInputStream(modelFile.fileDescriptor).channel
        val byteBuffer = fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            modelFile.startOffset,
            modelFile.declaredLength
        )
        interpreter = Interpreter(byteBuffer)
    }

    /**
     * Ð’Ñ‹Ñ‡Ð¸ÑÐ»ÑÐµÑ‚ ÑÐ¼Ð±ÐµÐ´Ð´Ð¸Ð½Ð³ Ð´Ð»Ñ ÑÑ‚Ñ€Ð¾ÐºÐ¸ Ñ‚ÐµÐºÑÑ‚Ð°
     */
    fun getEmbedding(text: String): FloatArray {
        // ÐŸÑ€ÐµÐ´Ð¾Ð±Ñ€Ð°Ð±Ð¾Ñ‚ÐºÐ° Ñ‚ÐµÐºÑÑ‚Ð° (Ñ‚Ð¾ÐºÐµÐ½Ð¸Ð·Ð°Ñ†Ð¸Ñ, Ð´Ð¾Ð±Ð°Ð²Ð»ÐµÐ½Ð¸Ðµ [CLS], [SEP], etc.)
        // Ð£Ð¿Ñ€Ð¾Ñ‰ÐµÐ½Ð½Ð°Ñ Ð²ÐµÑ€ÑÐ¸Ñ - Ð² Ñ€ÐµÐ°Ð»ÑŒÐ½Ð¾ÑÑ‚Ð¸ Ð·Ð´ÐµÑÑŒ Ð´Ð¾Ð»Ð¶ÐµÐ½ Ð±Ñ‹Ñ‚ÑŒ Ð¿Ð¾Ð»Ð½Ð¾Ñ†ÐµÐ½Ð½Ñ‹Ð¹ Ñ‚Ð¾ÐºÐµÐ½Ð¸Ð·Ð°Ñ‚Ð¾Ñ€
        val cleanedText = text.lowercase().take(128)
        
        // ÐŸÐ¾Ð´Ð³Ð¾Ñ‚Ð¾Ð²ÐºÐ° Ð²Ñ…Ð¾Ð´Ð½Ñ‹Ñ… Ð´Ð°Ð½Ð½Ñ‹Ñ… Ð´Ð»Ñ Ð¼Ð¾Ð´ÐµÐ»Ð¸
        val inputBuffer = ByteBuffer.allocateDirect(128 * 4) // 128 Ñ‚Ð¾ÐºÐµÐ½Ð¾Ð² Ð¿Ð¾ 4 Ð±Ð°Ð¹Ñ‚Ð°
        inputBuffer.order(ByteOrder.nativeOrder())
        
        // Ð—Ð°Ð¿Ð¾Ð»Ð½ÑÐµÐ¼ Ð±ÑƒÑ„ÐµÑ€ Ð´Ð°Ð½Ð½Ñ‹Ð¼Ð¸
        // Ð’ Ñ€ÐµÐ°Ð»ÑŒÐ½Ð¾ÑÑ‚Ð¸ Ð·Ð´ÐµÑÑŒ Ð´Ð¾Ð»Ð¶Ð½Ñ‹ Ð±Ñ‹Ñ‚ÑŒ ID Ñ‚Ð¾ÐºÐµÐ½Ð¾Ð², Ð½Ð¾ Ð´Ð»Ñ ÑƒÐ¿Ñ€Ð¾Ñ‰ÐµÐ½Ð¸Ñ Ð¸ÑÐ¿Ð¾Ð»ÑŒÐ·ÑƒÐµÐ¼ ASCII-ÐºÐ¾Ð´Ñ‹
        cleanedText.forEach { char ->
            inputBuffer.putFloat(char.code.toFloat())
        }
        
        // ÐŸÐ¾Ð´Ð³Ð¾Ñ‚Ð¾Ð²ÐºÐ° Ð²Ñ‹Ñ…Ð¾Ð´Ð½Ð¾Ð³Ð¾ Ð±ÑƒÑ„ÐµÑ€Ð°
        val outputBuffer = ByteBuffer.allocateDirect(embedding_size * 4)
        outputBuffer.order(ByteOrder.nativeOrder())
        
        // Ð—Ð°Ð¿ÑƒÑÐºÐ°ÐµÐ¼ inference
        interpreter.run(inputBuffer, outputBuffer)
        
        // ÐšÐ¾Ð½Ð²ÐµÑ€Ñ‚Ð¸Ñ€ÑƒÐµÐ¼ Ñ€ÐµÐ·ÑƒÐ»ÑŒÑ‚Ð°Ñ‚ Ð² FloatArray
        val embedding = FloatArray(embedding_size)
        outputBuffer.rewind()
        for (i in 0 until embedding_size) {
            embedding[i] = outputBuffer.float
        }
        
        // ÐÐ¾Ñ€Ð¼Ð°Ð»Ð¸Ð·ÑƒÐµÐ¼ Ð²ÐµÐºÑ‚Ð¾Ñ€
        return normalizeVector(embedding)
    }
    
    /**
     * Ð’Ñ‹Ñ‡Ð¸ÑÐ»ÑÐµÑ‚ ÐºÐ¾ÑÐ¸Ð½ÑƒÑÐ½Ð¾Ðµ ÑÑ…Ð¾Ð´ÑÑ‚Ð²Ð¾ Ð¼ÐµÐ¶Ð´Ñƒ Ð´Ð²ÑƒÐ¼Ñ Ð²ÐµÐºÑ‚Ð¾Ñ€Ð°Ð¼Ð¸
     */
    fun cosineSimilarity(vec1: FloatArray, vec2: FloatArray): Float {
        var dotProduct = 0f
        for (i in vec1.indices) {
            dotProduct += vec1[i] * vec2[i]
        }
        return dotProduct
    }
    
    /**
     * ÐÐ¾Ñ€Ð¼Ð°Ð»Ð¸Ð·ÑƒÐµÑ‚ Ð²ÐµÐºÑ‚Ð¾Ñ€ (L2-Ð½Ð¾Ñ€Ð¼Ð°Ð»Ð¸Ð·Ð°Ñ†Ð¸Ñ)
     */
    private fun normalizeVector(vector: FloatArray): FloatArray {
        var sumOfSquares = 0f
        for (value in vector) {
            sumOfSquares += value * value
        }
        val norm = sqrt(sumOfSquares)
        
        val normalizedVector = FloatArray(vector.size)
        for (i in vector.indices) {
            normalizedVector[i] = vector[i] / norm
        }
        return normalizedVector
    }
    
    /**
     * Ð—Ð°ÐºÑ€Ñ‹Ð²Ð°ÐµÑ‚ Ð¸Ð½Ñ‚ÐµÑ€Ð¿Ñ€ÐµÑ‚Ð°Ñ‚Ð¾Ñ€ TFLite
     */
    fun close() {
        interpreter.close()
    }
}
