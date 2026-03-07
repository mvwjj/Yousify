package com.mvwj.yousify.ui.viewmodel // Ð£Ð±ÐµÐ´Ð¸Ñ‚ÐµÑÑŒ, Ñ‡Ñ‚Ð¾ Ð¿Ð°ÐºÐµÑ‚ Ð¿Ñ€Ð°Ð²Ð¸Ð»ÑŒÐ½Ñ‹Ð¹

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.mvwj.yousify.ui.YousifyViewModel // Ð˜Ð¼Ð¿Ð¾Ñ€Ñ‚ Ð²Ð°ÑˆÐµÐ¹ ViewModel

class YousifyViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(YousifyViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return YousifyViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}