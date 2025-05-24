package com.veshikov.yousify.auth

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object AuthEvents {
    private val _forceLogoutEvent = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1) // extraBufferCapacity для tryEmit
    val forceLogoutEvent = _forceLogoutEvent.asSharedFlow()

    /**
     * Attempts to emit a force logout event. Best effort.
     */
    fun triggerForceLogoutNonSuspend() {
        // Используем tryEmit для не-suspend контекста, если подписчик не успевает, событие может быть потеряно,
        // но для logout это обычно приемлемо, т.к. следующий API вызов всё равно провалится.
        val emitted = _forceLogoutEvent.tryEmit(Unit)
        if (!emitted) {
            // Можно добавить лог, если эмит не удался, но для простого события это может быть излишне
            // com.veshikov.yousify.utils.Logger.w("AuthEvents: Failed to emit force logout event via tryEmit.")
        }
    }

    // Если нужно вызывать из suspend-контекста
    suspend fun triggerForceLogoutSuspend() {
        _forceLogoutEvent.emit(Unit)
    }
}