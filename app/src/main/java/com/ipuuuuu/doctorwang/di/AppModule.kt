package com.ipuuuuu.doctorwang.di

import android.content.Context
import androidx.room.Room
import com.ipuuuuu.doctorwang.data.repository.DoctorWangRepository
import com.ipuuuuu.doctorwang.data.repository.FakeDoctorWangRepository
import com.ipuuuuu.doctorwang.database.DoctorWangDatabase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindDoctorWangRepository(
        repository: FakeDoctorWangRepository,
    ): DoctorWangRepository
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DoctorWangDatabase {
        return Room.databaseBuilder(
            context,
            DoctorWangDatabase::class.java,
            "doctor_wang.db",
        ).build()
    }
}
