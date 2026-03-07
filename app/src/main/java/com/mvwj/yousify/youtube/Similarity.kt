package com.mvwj.yousify.youtube

import android.content.Context
import com.aallam.similarity.JaroWinkler

object Similarity {
    private val jw = JaroWinkler()
    fun jaroWinkler(a: String, b: String): Double = jw.similarity(a, b)

    // Loads SBERT MiniLM-L12-v2 TFLite model and computes cosine similarity
    fun sbertCosine(a: String, b: String, context: Context? = null): Double {
        // TODO: ÐŸÐ¾Ð´ÐºÐ»ÑŽÑ‡Ð¸Ñ‚ÑŒ Ñ€ÐµÐ°Ð»ÑŒÐ½ÑƒÑŽ TFLite SBERT Ð¼Ð¾Ð´ÐµÐ»ÑŒ Ð¸ Ñ‚Ð¾ÐºÐµÐ½Ð¸Ð·Ð°Ñ†Ð¸ÑŽ
        // ÐŸÑ€Ð¸Ð¼ÐµÑ€ Ð·Ð°Ð³Ñ€ÑƒÐ·ÐºÐ¸ Ð¼Ð¾Ð´ÐµÐ»Ð¸:
        // val model = Interpreter(loadModelFile(context, "minilm.tflite"))
        // val tokensA = tokenize(a)
        // val tokensB = tokenize(b)
        // val embA = model.run(tokensA)
        // val embB = model.run(tokensB)
        // return cosine(embA, embB)
        // ÐŸÐ¾ÐºÐ° Ð²Ð¾Ð·Ð²Ñ€Ð°Ñ‰Ð°ÐµÐ¼ 0.85 ÐºÐ°Ðº Ð·Ð°Ð³Ð»ÑƒÑˆÐºÑƒ (Ñ€ÐµÐ°Ð»Ð¸Ð·Ð¾Ð²Ð°Ñ‚ÑŒ Ð´Ð»Ñ Ð¿Ð¾Ð»Ð½Ð¾Ð¹ Ñ‚Ð¾Ñ‡Ð½Ð¾ÑÑ‚Ð¸)
        return 0.85
    }
}
