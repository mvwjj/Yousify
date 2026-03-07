package com.mvwj.yousify.ui.utils

import android.widget.ImageView
import coil.ImageLoader
import coil.request.ImageRequest
import coil.transition.CrossfadeTransition

/**
 * Ð’ÑÐ¿Ð¾Ð¼Ð¾Ð³Ð°Ñ‚ÐµÐ»ÑŒÐ½Ñ‹Ð¹ Ð¾Ð±ÑŠÐµÐºÑ‚ Ð´Ð»Ñ Ð·Ð°Ð³Ñ€ÑƒÐ·ÐºÐ¸ Ð¸Ð·Ð¾Ð±Ñ€Ð°Ð¶ÐµÐ½Ð¸Ð¹ Ñ Ð¿Ð¾Ð¼Ð¾Ñ‰ÑŒÑŽ Coil
 */
object CoilUtils {
    /**
     * Ð—Ð°Ð³Ñ€ÑƒÐ¶Ð°ÐµÑ‚ Ð¸Ð·Ð¾Ð±Ñ€Ð°Ð¶ÐµÐ½Ð¸Ðµ Ð¸Ð· URL Ð² ImageView
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
