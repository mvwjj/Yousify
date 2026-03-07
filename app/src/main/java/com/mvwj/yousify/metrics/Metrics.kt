package com.mvwj.yousify.metrics

import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong

object Metrics {
    private val searchLatencySum = AtomicLong(0)
    private val searchCount = AtomicLong(0)
    private val cacheHits = AtomicLong(0)
    private val cacheMisses = AtomicLong(0)
    private val skippedSeconds = AtomicLong(0)

    fun logSearchLatency(ms: Long) {
        searchLatencySum.addAndGet(ms)
        searchCount.incrementAndGet()
        Timber.i("[Metrics] Search latency: ${ms}ms")
    }

    fun logCacheHit(hit: Boolean) {
        if (hit) cacheHits.incrementAndGet() else cacheMisses.incrementAndGet()
        Timber.i("[Metrics] Cache hit: $hit")
    }

    fun logSkipped(seconds: Long) {
        skippedSeconds.addAndGet(seconds)
        Timber.i("[Metrics] Skipped seconds: $seconds")
    }

    fun report() {
        val avgLatency = if (searchCount.get() > 0) searchLatencySum.get() / searchCount.get() else 0
        val hitRate = if (cacheHits.get() + cacheMisses.get() > 0) 100 * cacheHits.get() / (cacheHits.get() + cacheMisses.get()) else 0
        Timber.i("[Metrics] avgLatencyMs=$avgLatency, cacheHitRate=$hitRate%, skippedSeconds=${skippedSeconds.get()}")
    }
}
