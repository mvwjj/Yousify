package com.veshikov.yousify

import com.veshikov.yousify.youtube.SearchEngine
import com.veshikov.yousify.youtube.TrackInput
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SearchEngineTest {
    @Test
    fun testIsrcPath() = runBlocking {
        val input = TrackInput("Artist", "Title", 180_000, "TESTISRC123", "")
        val mock = MockExtractor(isrcHit = true)
        val result = SearchEngine.searchWithExtractor(input, mock)
        assertNotNull(result)
        assertEquals(100, result?.score)
    }

    @Test
    fun testTextPath() = runBlocking {
        val input = TrackInput("Artist", "Title", 180_000, "NOISRC", "")
        val mock = MockExtractor(textHit = true)
        val result = SearchEngine.searchWithExtractor(input, mock)
        assertNotNull(result)
        assertTrue(result!!.score in 70..99)
    }

    @Test
    fun testFpPath() = runBlocking {
        val input = TrackInput("Artist", "Title", 180_000, "NOISRC", "")
        val mock = MockExtractor(fpHit = true)
        val result = SearchEngine.searchWithExtractor(input, mock)
        assertNotNull(result)
        assertEquals(70, result?.score)
    }
}

// MockExtractor simulates NewPipeExtractor and fpcalc
class MockExtractor(
    val isrcHit: Boolean = false,
    val textHit: Boolean = false,
    val fpHit: Boolean = false
) {
    fun searchIsrc(input: TrackInput) = if (isrcHit) listOf(MockItem(100)) else emptyList()
    fun searchText(input: TrackInput) = if (textHit) listOf(MockItem(80)) else emptyList()
    fun fingerprint(input: TrackInput) = fpHit
}
data class MockItem(val score: Int)
