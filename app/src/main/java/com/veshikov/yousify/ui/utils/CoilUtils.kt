package com.veshikov.yousify.ui.utils

import android.widget.ImageView
import coil.ImageLoader
import coil.request.ImageRequest
import coil.transition.CrossfadeTransition

/**
 * Вспомогательный объект для загрузки изображений с помощью Coil
 */
object CoilUtils {
    /**
     * Загружает изображение из URL в ImageView
     */
    fun loadImage(imageView: ImageView, url: String?, imageLoader: ImageLoader, builder: ImageRequest.Builder.() -> Unit = {}) {
        val context = imageView.context
        val request = ImageRequest.Builder(context)
            .data(url)
            .apply(builder)
            .transitionFactory(CrossfadeTransition.Factory())
            .build()
        imageLoader.enqueue(request)
    }
}
