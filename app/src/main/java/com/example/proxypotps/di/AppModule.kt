package com.example.proxypotps.di

import android.content.Context
import androidx.room.Room
import com.example.proxypotps.data.local.AppDatabase
import com.example.proxypotps.data.local.NodeDao
import com.example.proxypotps.data.local.StressTestDao
import com.example.proxypotps.data.local.MIGRATION_3_4
import com.example.proxypotps.data.local.MIGRATION_4_5
import com.example.proxypotps.data.local.MIGRATION_5_6
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "proxypot.db")
            .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideNodeDao(database: AppDatabase): NodeDao = database.nodeDao()

    @Provides
    fun provideStressTestDao(database: AppDatabase): StressTestDao = database.stressTestDao()

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
