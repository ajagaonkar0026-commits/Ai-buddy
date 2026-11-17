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

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val category: String = "General",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val dueTimeText: String = "Today",
    val priority: String = "Normal",
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String, // "user" or "assistant"
    val text: String,
    val providerTag: String,
    val isPlaceholderAi: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "automation_logs")
data class AutomationLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workflowName: String,
    val summary: String,
    val status: String, // "Completed", "Cancelled", "Needs Confirmation", "Offline"
    val stepsDetail: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity): Long

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY isCompleted ASC, createdAt DESC")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: ReminderEntity): Long

    @Query("UPDATE reminders SET isCompleted = :completed WHERE id = :id")
    suspend fun setCompleted(id: Long, completed: Boolean)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun observeAll(): Flow<List<ChatMessageEntity>>

    @Insert
    suspend fun insert(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages")
    suspend fun clearAll()
}

@Dao
interface AutomationLogDao {
    @Query("SELECT * FROM automation_logs ORDER BY timestamp DESC LIMIT 50")
    fun observeAll(): Flow<List<AutomationLogEntity>>

    @Insert
    suspend fun insert(log: AutomationLogEntity): Long

    @Query("DELETE FROM automation_logs")
    suspend fun clearAll()
}

@Database(
    entities = [NoteEntity::class, ReminderEntity::class, ChatMessageEntity::class, AutomationLogEntity::class],
    version = 1,
    exportSchema = false
)
abstract class JarvisDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun reminderDao(): ReminderDao
    abstract fun chatDao(): ChatDao
    abstract fun automationLogDao(): AutomationLogDao

    companion object {
        @Volatile
        private var INSTANCE: JarvisDatabase? = null

        fun getInstance(context: Context): JarvisDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    JarvisDatabase::class.java,
                    "jarvis_assistant.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

class JarvisLocalRepository(private val db: JarvisDatabase) {
    val notes: Flow<List<NoteEntity>> = db.noteDao().observeAll()
    val reminders: Flow<List<ReminderEntity>> = db.reminderDao().observeAll()
    val chatMessages: Flow<List<ChatMessageEntity>> = db.chatDao().observeAll()
    val automationLogs: Flow<List<AutomationLogEntity>> = db.automationLogDao().observeAll()

    suspend fun saveNote(title: String, content: String, category: String = "General") {
        db.noteDao().insert(NoteEntity(title = title.trim(), content = content.trim(), category = category))
    }

    suspend fun deleteNote(id: Long) = db.noteDao().deleteById(id)

    suspend fun addReminder(title: String, dueTimeText: String = "Today", priority: String = "Normal") {
        db.reminderDao().insert(
            ReminderEntity(title = title.trim(), dueTimeText = dueTimeText, priority = priority)
        )
    }

    suspend fun toggleReminder(id: Long, completed: Boolean) = db.reminderDao().setCompleted(id, completed)

    suspend fun deleteReminder(id: Long) = db.reminderDao().deleteById(id)

    suspend fun appendChat(role: String, text: String, providerTag: String, isPlaceholderAi: Boolean = false) {
        db.chatDao().insert(
            ChatMessageEntity(
                role = role,
                text = text,
                providerTag = providerTag,
                isPlaceholderAi = isPlaceholderAi
            )
        )
    }

    suspend fun clearChat() = db.chatDao().clearAll()

    suspend fun logAutomation(workflowName: String, summary: String, status: String, stepsDetail: String) {
        db.automationLogDao().insert(
            AutomationLogEntity(
                workflowName = workflowName,
                summary = summary,
                status = status,
                stepsDetail = stepsDetail
            )
        )
    }

    suspend fun clearAutomationLogs() = db.automationLogDao().clearAll()
}
