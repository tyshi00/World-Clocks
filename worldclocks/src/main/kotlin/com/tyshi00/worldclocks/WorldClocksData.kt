package com.tyshi00.worldclocks

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update

// ── Entities ─────────────────────────────────────────────────────────────

@Entity(tableName = "saved_locations")
data class SavedLocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val city: String,
    val region: String?, // state / province, when the geocoder has one
    val country: String?,
    val timeZoneId: String, // IANA id, e.g. "Asia/Tokyo"
    val latitude: Double,
    val longitude: Double,
    val sortOrder: Int,
)

/** Generic key/value store for app-wide settings, same shape as preferences tables elsewhere in Light tools. */
@Entity(tableName = "preferences")
data class PreferenceEntity(
    @PrimaryKey val key: String,
    val value: String,
)

// ── DAOs ─────────────────────────────────────────────────────────────────

@Dao
interface SavedLocationDao {
    @Query("SELECT * FROM saved_locations ORDER BY sortOrder ASC, id ASC")
    suspend fun getAll(): List<SavedLocationEntity>

    @Query("SELECT * FROM saved_locations WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SavedLocationEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM saved_locations")
    suspend fun getMaxSortOrder(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SavedLocationEntity): Long

    @Update
    suspend fun update(entity: SavedLocationEntity)

    @Query("DELETE FROM saved_locations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM saved_locations")
    suspend fun deleteAll()
}

@Dao
interface PreferenceDao {
    @Query("SELECT * FROM preferences WHERE key = :key LIMIT 1")
    suspend fun get(key: String): PreferenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(entity: PreferenceEntity)

    @Query("DELETE FROM preferences WHERE key = :key")
    suspend fun delete(key: String)
}

// ── Database ─────────────────────────────────────────────────────────────

@Database(
    entities = [SavedLocationEntity::class, PreferenceEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class WorldClocksDatabase : RoomDatabase() {
    abstract fun savedLocationDao(): SavedLocationDao
    abstract fun preferenceDao(): PreferenceDao
}
