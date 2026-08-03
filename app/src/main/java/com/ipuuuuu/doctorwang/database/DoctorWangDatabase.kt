package com.ipuuuuu.doctorwang.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ipuuuuu.doctorwang.database.dao.MessageDao
import com.ipuuuuu.doctorwang.database.entity.MessageEntity

@Database(entities = [MessageEntity::class], version = 2, exportSchema = false)
abstract class DoctorWangDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
}
