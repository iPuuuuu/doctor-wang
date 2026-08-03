package com.ipuuuuu.doctorwang.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ipuuuuu.doctorwang.database.entity.MessageEntity

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE userId = :userId ORDER BY timestamp ASC")
    suspend fun getAll(userId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Query("UPDATE messages SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)
}
