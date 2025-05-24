package com.veshikov.yousify.ui.viewmodel // Убедитесь, что пакет правильный

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.veshikov.yousify.ui.YousifyViewModel // Импорт вашей ViewModel

class YousifyViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(YousifyViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return YousifyViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}