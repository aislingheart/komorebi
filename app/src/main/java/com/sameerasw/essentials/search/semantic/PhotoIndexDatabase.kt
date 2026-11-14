package com.sameerasw.essentials.search.semantic

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import java.nio.ByteBuffer
import java.nio.ByteOrder

@Entity(tableName = "photo_embeddings")
data class PhotoIndexEntity(
    @PrimaryKey val mediaId: Long,
    val contentUri: String,
    val displayName: String,
    val bucketName: String,
    val dateTaken: Long,
    val ocrText: String,
    val sceneTags: String,
    val embeddingBlob: ByteArray,
    val indexedAt: Long,
) {
    fun toFloatArray(): FloatArray {
        val buf = ByteBuffer.wrap(embeddingBlob).order(ByteOrder.LITTLE_ENDIAN)
        val arr = FloatArray(embeddingBlob.size / 4)
        for (i in arr.indices) arr[i] = buf.getFloat()
        return arr
    }

    companion object {
        fun fromFloatArray(arr: FloatArray): ByteArray {
            val buf = ByteBuffer.allocate(arr.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            for (v in arr) buf.putFloat(v)
            return buf.array()
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PhotoIndexEntity) return false
        return mediaId == other.mediaId
    }

    override fun hashCode(): Int = mediaId.hashCode()
}

@Dao
interface PhotoIndexDao {
    @Query("SELECT * FROM photo_embeddings ORDER BY dateTaken DESC")
    suspend fun getAll(): List<PhotoIndexEntity>

    @Query("SELECT mediaId FROM photo_embeddings")
    suspend fun getAllIds(): List<Long>

    @Query("SELECT COUNT(*) FROM photo_embeddings")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<PhotoIndexEntity>)

    @Query("DELETE FROM photo_embeddings")
    suspend fun clearAll()
}

@Database(entities = [PhotoIndexEntity::class], version = 1, exportSchema = false)
abstract class PhotoIndexDatabase : RoomDatabase() {
    abstract fun photoIndexDao(): PhotoIndexDao

    companion object {
        @Volatile
        private var instance: PhotoIndexDatabase? = null

        fun getInstance(context: Context): PhotoIndexDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PhotoIndexDatabase::class.java,
                    "semantic_photo_index.db",
                ).build().also { instance = it }
            }
        }
    }
}
