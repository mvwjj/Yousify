package com.mvwj.yousify.auth

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object AuthEvents {
    private val _forceLogoutEvent = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1) // extraBufferCapacity Ð´Ð»Ñ tryEmit
    val forceLogoutEvent = _forceLogoutEvent.asSharedFlow()

    /**
     * Attempts to emit a force logout event. Best effort.
     */
    fun triggerForceLogoutNonSuspend() {
        // Ð˜ÑÐ¿Ð¾Ð»ÑŒÐ·ÑƒÐµÐ¼ tryEmit Ð´Ð»Ñ Ð½Ðµ-suspend ÐºÐ¾Ð½Ñ‚ÐµÐºÑÑ‚Ð°, ÐµÑÐ»Ð¸ Ð¿Ð¾Ð´Ð¿Ð¸ÑÑ‡Ð¸Ðº Ð½Ðµ ÑƒÑÐ¿ÐµÐ²Ð°ÐµÑ‚, ÑÐ¾Ð±Ñ‹Ñ‚Ð¸Ðµ Ð¼Ð¾Ð¶ÐµÑ‚ Ð±Ñ‹Ñ‚ÑŒ Ð¿Ð¾Ñ‚ÐµÑ€ÑÐ½Ð¾,
        // Ð½Ð¾ Ð´Ð»Ñ logout ÑÑ‚Ð¾ Ð¾Ð±Ñ‹Ñ‡Ð½Ð¾ Ð¿Ñ€Ð¸ÐµÐ¼Ð»ÐµÐ¼Ð¾, Ñ‚.Ðº. ÑÐ»ÐµÐ´ÑƒÑŽÑ‰Ð¸Ð¹ API Ð²Ñ‹Ð·Ð¾Ð² Ð²ÑÑ‘ Ñ€Ð°Ð²Ð½Ð¾ Ð¿Ñ€Ð¾Ð²Ð°Ð»Ð¸Ñ‚ÑÑ.
        val emitted = _forceLogoutEvent.tryEmit(Unit)
        if (!emitted) {
            // ÐœÐ¾Ð¶Ð½Ð¾ Ð´Ð¾Ð±Ð°Ð²Ð¸Ñ‚ÑŒ Ð»Ð¾Ð³, ÐµÑÐ»Ð¸ ÑÐ¼Ð¸Ñ‚ Ð½Ðµ ÑƒÐ´Ð°Ð»ÑÑ, Ð½Ð¾ Ð´Ð»Ñ Ð¿Ñ€Ð¾ÑÑ‚Ð¾Ð³Ð¾ ÑÐ¾Ð±Ñ‹Ñ‚Ð¸Ñ ÑÑ‚Ð¾ Ð¼Ð¾Ð¶ÐµÑ‚ Ð±Ñ‹Ñ‚ÑŒ Ð¸Ð·Ð»Ð¸ÑˆÐ½Ðµ
            // com.mvwj.yousify.utils.Logger.w("AuthEvents: Failed to emit force logout event via tryEmit.")
        }
    }

    // Ð•ÑÐ»Ð¸ Ð½ÑƒÐ¶Ð½Ð¾ Ð²Ñ‹Ð·Ñ‹Ð²Ð°Ñ‚ÑŒ Ð¸Ð· suspend-ÐºÐ¾Ð½Ñ‚ÐµÐºÑÑ‚Ð°
    suspend fun triggerForceLogoutSuspend() {
        _forceLogoutEvent.emit(Unit)
    }
}