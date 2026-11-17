package com.example.data.local

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
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "memory_preferences")
data class MemoryPreferenceEntity(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val scheduleText: String,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface MemoryPreferenceDao {
    @Query("SELECT * FROM memory_preferences ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<MemoryPreferenceEntity>>

    @Query("SELECT * FROM memory_preferences ORDER BY updatedAt DESC")
    suspend fun getAll(): List<MemoryPreferenceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MemoryPreferenceEntity)

    @Query("DELETE FROM memory_preferences WHERE key = :key")
    suspend fun deleteByKey(key: String)

    @Query("DELETE FROM memory_preferences")
    suspend fun clearAll()
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: ReminderEntity): Long

    @Query("UPDATE reminders SET isCompleted = :completed WHERE id = :id")
    suspend fun setCompleted(id: Long, completed: Boolean)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Database(
    entities = [MemoryPreferenceEntity::class, ReminderEntity::class],
    version = 1,
    exportSchema = false
)
abstract class BrstiDatabase : RoomDatabase() {
    abstract fun memoryPreferenceDao(): MemoryPreferenceDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        @Volatile
        private var INSTANCE: BrstiDatabase? = null

        fun getInstance(context: Context): BrstiDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BrstiDatabase::class.java,
                    "brsti_assistant.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

class MemoryAndReminderRepository(private val database: BrstiDatabase) {
    private val memoryDao = database.memoryPreferenceDao()
    private val reminderDao = database.reminderDao()

    val memories: Flow<List<MemoryPreferenceEntity>> = memoryDao.observeAll()
    val reminders: Flow<List<ReminderEntity>> = reminderDao.observeAll()

    suspend fun getMemoriesSnapshot(): List<MemoryPreferenceEntity> = memoryDao.getAll()

    suspend fun saveMemory(key: String, value: String) {
        memoryDao.upsert(MemoryPreferenceEntity(key = key.trim(), value = value.trim()))
    }

    suspend fun deleteMemory(key: String) = memoryDao.deleteByKey(key)

    suspend fun clearMemories() = memoryDao.clearAll()

    suspend fun addReminder(title: String, scheduleText: String): Long {
        return reminderDao.insert(ReminderEntity(title = title.trim(), scheduleText = scheduleText.trim()))
    }

    suspend fun toggleReminder(id: Long, completed: Boolean) = reminderDao.setCompleted(id, completed)

    suspend fun deleteReminder(id: Long) = reminderDao.deleteById(id)
}
