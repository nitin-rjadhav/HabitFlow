/*
 * Copyright 2023 Olivér Falvai
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ofalvai.habittracker.core.database

import android.app.Application
import android.content.SharedPreferences
import android.preference.PreferenceManager
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PersistenceModule {
    @Provides
    @Singleton
    fun provideDb(app: Application): AppDatabase {
        // Do not log SQL queries or bind arguments. They can contain user habit
        // data and Health Connect exercise-source identifiers, which must not
        // be written to Logcat even for normal debug builds.
        return Room.databaseBuilder(app, AppDatabase::class.java, "app-db")
            .addMigrations(*MIGRATIONS)
            .build()
    }

    @Provides
    @Singleton
    fun provideHabitDao(db: AppDatabase): HabitDao = db.habitDao()

    @Provides
    @Singleton
    fun provideExerciseMappingDao(db: AppDatabase): ExerciseMappingDao = db.exerciseMappingDao()

    @Provides
    @Singleton
    fun provideExerciseAutoCompletionDao(db: AppDatabase): ExerciseAutoCompletionDao = db.exerciseAutoCompletionDao()

    @Provides
    @Singleton
    fun provideSharedPreferences(app: Application): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(app)
}
