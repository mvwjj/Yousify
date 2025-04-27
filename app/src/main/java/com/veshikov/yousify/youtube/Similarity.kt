package com.veshikov.yousify.youtube

import android.content.Context
import com.aallam.similarity.JaroWinkler

object Similarity {
    private val jw = JaroWinkler()
    fun jaroWinkler(a: String, b: String): Double = jw.similarity(a, b)

    // Loads SBERT MiniLM-L12-v2 TFLite model and computes cosine similarity
    fun sbertCosine(a: String, b: String, context: Context? = null): Double {
        // TODO: Подключить реальную TFLite SBERT модель и токенизацию
        // Пример загрузки модели:
        // val model = Interpreter(loadModelFile(context, "minilm.tflite"))
        // val tokensA = tokenize(a)
        // val tokensB = tokenize(b)
        // val embA = model.run(tokensA)
        // val embB = model.run(tokensB)
        // return cosine(embA, embB)
        // Пока возвращаем 0.85 как заглушку (реализовать для полной точности)
        return 0.85
    }
}
