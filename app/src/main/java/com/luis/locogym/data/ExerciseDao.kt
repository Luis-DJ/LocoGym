package com.luis.locogym.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Insert
    suspend fun insert(entry: ExerciseEntry): Long

    @Query("SELECT * FROM exercise_entries ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<ExerciseEntry>>
}
