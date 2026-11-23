package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM user_profiles ORDER BY createdAt ASC")
    fun observeAllProfiles(): Flow<List<UserProfileEntity>>

    @Query("SELECT * FROM user_profiles WHERE isActive = 1 LIMIT 1")
    fun observeActiveProfile(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profiles WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveProfile(): UserProfileEntity?

    @Query("SELECT * FROM user_profiles WHERE id = :id LIMIT 1")
    suspend fun getProfileById(id: String): UserProfileEntity?

    @Query("SELECT COUNT(*) FROM user_profiles")
    suspend fun countProfiles(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: UserProfileEntity)

    @Query("UPDATE user_profiles SET isActive = CASE WHEN id = :profileId THEN 1 ELSE 0 END")
    suspend fun setActiveProfile(profileId: String)

    @Query("DELETE FROM user_profiles WHERE id = :profileId")
    suspend fun deleteProfile(profileId: String)
}

@Dao
interface ProviderConfigDao {
    @Query("SELECT * FROM provider_configs WHERE profileId = :profileId")
    fun observeConfigsForProfile(profileId: String): Flow<List<ProviderConfigEntity>>

    @Query("SELECT * FROM provider_configs WHERE profileId = :profileId AND providerId = :providerId LIMIT 1")
    suspend fun getConfig(profileId: String, providerId: String): ProviderConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConfig(config: ProviderConfigEntity)

    @Query("DELETE FROM provider_configs WHERE profileId = :profileId")
    suspend fun deleteAllForProfile(profileId: String)
}

@Dao
interface VoiceConfigDao {
    @Query("SELECT * FROM voice_configs WHERE profileId = :profileId LIMIT 1")
    fun observeVoiceConfig(profileId: String): Flow<VoiceConfigEntity?>

    @Query("SELECT * FROM voice_configs WHERE profileId = :profileId LIMIT 1")
    suspend fun getVoiceConfig(profileId: String): VoiceConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVoiceConfig(config: VoiceConfigEntity)

    @Query("DELETE FROM voice_configs WHERE profileId = :profileId")
    suspend fun deleteForProfile(profileId: String)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages WHERE profileId = :profileId ORDER BY timestamp ASC")
    fun observeMessages(profileId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE profileId = :profileId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(profileId: String, limit: Int): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages WHERE profileId = :profileId")
    suspend fun clearForProfile(profileId: String)
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memory_entries WHERE profileId = :profileId ORDER BY updatedAt DESC")
    fun observeMemories(profileId: String): Flow<List<MemoryEntryEntity>>

    @Query("SELECT * FROM memory_entries WHERE profileId = :profileId ORDER BY updatedAt DESC")
    suspend fun getMemories(profileId: String): List<MemoryEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMemory(entry: MemoryEntryEntity): Long

    @Update
    suspend fun updateMemory(entry: MemoryEntryEntity)

    @Query("DELETE FROM memory_entries WHERE id = :id")
    suspend fun deleteMemory(id: Long)

    @Query("DELETE FROM memory_entries WHERE profileId = :profileId")
    suspend fun deleteAllForProfile(profileId: String)
}

@Dao
interface KnowledgeDao {
    @Query("SELECT * FROM knowledge_docs WHERE profileId = :profileId ORDER BY createdAt DESC")
    fun observeDocs(profileId: String): Flow<List<KnowledgeDocEntity>>

    @Query("SELECT * FROM knowledge_docs WHERE profileId = :profileId ORDER BY createdAt DESC")
    suspend fun getDocs(profileId: String): List<KnowledgeDocEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDoc(doc: KnowledgeDocEntity): Long

    @Query("DELETE FROM knowledge_docs WHERE id = :id")
    suspend fun deleteDoc(id: Long)

    @Query("DELETE FROM knowledge_docs WHERE profileId = :profileId")
    suspend fun deleteAllForProfile(profileId: String)
}

@Dao
interface AutomationDao {
    @Query("SELECT * FROM automation_tasks WHERE profileId = :profileId ORDER BY createdAt DESC")
    fun observeTasks(profileId: String): Flow<List<AutomationTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: AutomationTaskEntity): Long

    @Query("DELETE FROM automation_tasks WHERE id = :id")
    suspend fun deleteTask(id: Long)

    @Query("DELETE FROM automation_tasks WHERE profileId = :profileId")
    suspend fun deleteAllForProfile(profileId: String)
}
