package com.veshikov.yousify.youtube

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.sqrt

/**
 * Класс для работы с моделью SBERT (paraphrase-multilingual-MiniLM-L12-v2)
 * Используется для семантического сравнения текстов
 */
class SbertModel(context: Context) {
    private val interpreter: Interpreter
    private val embedding_size = 384  // Размер эмбеддинга для MiniLM-L12-v2

    init {
        // Загрузка модели из assets
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
     * Вычисляет эмбеддинг для строки текста
     */
    fun getEmbedding(text: String): FloatArray {
        // Предобработка текста (токенизация, добавление [CLS], [SEP], etc.)
        // Упрощенная версия - в реальности здесь должен быть полноценный токенизатор
        val cleanedText = text.lowercase().take(128)
        
        // Подготовка входных данных для модели
        val inputBuffer = ByteBuffer.allocateDirect(128 * 4) // 128 токенов по 4 байта
        inputBuffer.order(ByteOrder.nativeOrder())
        
        // Заполняем буфер данными
        // В реальности здесь должны быть ID токенов, но для упрощения используем ASCII-коды
        cleanedText.forEach { char ->
            inputBuffer.putFloat(char.code.toFloat())
        }
        
        // Подготовка выходного буфера
        val outputBuffer = ByteBuffer.allocateDirect(embedding_size * 4)
        outputBuffer.order(ByteOrder.nativeOrder())
        
        // Запускаем inference
        interpreter.run(inputBuffer, outputBuffer)
        
        // Конвертируем результат в FloatArray
        val embedding = FloatArray(embedding_size)
        outputBuffer.rewind()
        for (i in 0 until embedding_size) {
            embedding[i] = outputBuffer.float
        }
        
        // Нормализуем вектор
        return normalizeVector(embedding)
    }
    
    /**
     * Вычисляет косинусное сходство между двумя векторами
     */
    fun cosineSimilarity(vec1: FloatArray, vec2: FloatArray): Float {
        var dotProduct = 0f
        for (i in vec1.indices) {
            dotProduct += vec1[i] * vec2[i]
        }
        return dotProduct
    }
    
    /**
     * Нормализует вектор (L2-нормализация)
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
     * Закрывает интерпретатор TFLite
     */
    fun close() {
        interpreter.close()
    }
}
