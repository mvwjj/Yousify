package com.veshikov.yousify.player

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.URL

@Entity(tableName = "sb_cache")
data class SponsorSegment(
    @PrimaryKey val videoId: String,
    val segmentsJson: String, // JSON string of list of pairs [[start,end],...]
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface SponsorBlockDao {
    @Query("SELECT * FROM sb_cache WHERE videoId = :videoId LIMIT 1")
    suspend fun getByVideoId(videoId: String): SponsorSegment?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(segment: SponsorSegment)
}

@Database(entities = [SponsorSegment::class], version = 1, exportSchema = false)
abstract class SponsorBlockDatabase : RoomDatabase() {
    abstract fun sponsorBlockDao(): SponsorBlockDao
    companion object {
        @Volatile private var INSTANCE: SponsorBlockDatabase? = null
        fun getInstance(context: Context): SponsorBlockDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                SponsorBlockDatabase::class.java,
                "sb_cache.db"
            ).build().also { INSTANCE = it }
        }
    }
}

object SponsorBlockApi {
    suspend fun fetchSegments(videoId: String): List<Pair<Float, Float>> = withContext(Dispatchers.IO) {
        val cats = "[\"sponsor\",\"intro\",\"outro\",\"selfpromo\"]"
        val url = "https://sponsor.ajay.app/api/skipSegments?videoID=$videoId&categories=$cats"
        try {
            val json = URL(url).readText()
            val arr = JSONArray(json)
            val result = mutableListOf<Pair<Float, Float>>()
            for (i in 0 until arr.length()) {
                val seg = arr.getJSONObject(i).getJSONArray("segment")
                result.add(seg.getDouble(0).toFloat() to seg.getDouble(1).toFloat())
            }
            result
        } catch (e: Exception) {
            // Если не удалось получить сегменты (например, 404), возвращаем пустой список
            emptyList()
        }
    }
    suspend fun getOrFetch(context: Context, videoId: String): List<Pair<Float, Float>> {
        val dao = SponsorBlockDatabase.getInstance(context).sponsorBlockDao()
        val cached = dao.getByVideoId(videoId)
        if (cached != null) {
            val arr = JSONArray(cached.segmentsJson)
            return List(arr.length()) { i ->
                val seg = arr.getJSONArray(i)
                seg.getDouble(0).toFloat() to seg.getDouble(1).toFloat()
            }
        }
        val segments = fetchSegments(videoId)
        val jsonArr = JSONArray().apply {
            segments.forEach { (start, end) -> put(JSONArray(listOf(start, end))) }
        }
        dao.insert(SponsorSegment(videoId, jsonArr.toString()))
        return segments
    }
}
