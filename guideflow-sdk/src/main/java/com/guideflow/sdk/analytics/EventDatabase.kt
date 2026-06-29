package com.guideflow.sdk.analytics

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

/** One queued analytics event. The full event is kept as JSON in [payloadJson]. */
@Entity(tableName = "guideflow_events")
internal data class AnalyticsEventEntity(
    @PrimaryKey val eventId: String,
    val createdAt: Long,
    val payloadJson: String,
)

@Dao
internal interface EventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: AnalyticsEventEntity)

    @Query("SELECT * FROM guideflow_events ORDER BY createdAt ASC LIMIT :limit")
    suspend fun oldest(limit: Int): List<AnalyticsEventEntity>

    @Query("DELETE FROM guideflow_events WHERE eventId IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("SELECT COUNT(*) FROM guideflow_events")
    suspend fun count(): Int

    // Trim the oldest n rows when the queue is over the cap.
    @Query("DELETE FROM guideflow_events WHERE eventId IN (SELECT eventId FROM guideflow_events ORDER BY createdAt ASC LIMIT :n)")
    suspend fun deleteOldest(n: Int)
}

@Database(entities = [AnalyticsEventEntity::class], version = 1, exportSchema = false)
internal abstract class EventDatabase : RoomDatabase() {
    abstract fun events(): EventDao

    companion object {
        @Volatile private var instance: EventDatabase? = null

        fun get(context: Context): EventDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, EventDatabase::class.java, "guideflow_events.db",
            ).build().also { instance = it }
        }
    }
}
