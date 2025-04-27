package com.veshikov.yousify.ann

class VectorCache(indexPath: String) {
    companion object {
        init {
            System.loadLibrary("faisshelper")
        }
    }
    private val nativeHandle: Long = nativeInit(indexPath)

    external fun nativeInit(indexPath: String): Long
    external fun add(id: Long, vec: FloatArray)
    external fun search(vec: FloatArray, k: Int): LongArray

    fun close() {
        // Optionally implement native cleanup
    }
}
