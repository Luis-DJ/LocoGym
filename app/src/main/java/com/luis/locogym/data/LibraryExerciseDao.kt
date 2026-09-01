package com.luis.locogym.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryExerciseDao {
    @Query("SELECT * FROM library_exercises ORDER BY archived, name COLLATE NOCASE")
    fun observeAll(): Flow<List<LibraryExercise>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(exercise: LibraryExercise): Long

    @Update
    suspend fun update(exercise: LibraryExercise)

    @Query("SELECT * FROM library_exercises WHERE normalizedName = :normalizedName LIMIT 1")
    suspend fun findByNormalizedName(normalizedName: String): LibraryExercise?

    @Query("UPDATE library_exercises SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    suspend fun save(exercise: LibraryExercise) {
        val cleanName = exercise.name.trim()
        require(cleanName.isNotBlank()) { "Exercise name is required." }
        val clean = exercise.copy(name = cleanName, normalizedName = cleanName.lowercase())
        if (clean.id == 0L) insert(clean) else update(clean)
    }

    @Transaction
    suspend fun addMissing(exercises: List<TemplateExercise>) {
        exercises.forEach { exercise ->
            val normalized = exercise.name.trim().lowercase()
            if (findByNormalizedName(normalized) == null) {
                insert(
                    LibraryExercise(
                        name = exercise.name.trim(),
                        normalizedName = normalized,
                        defaultWeightKg = exercise.targetWeightKg,
                        defaultSets = exercise.targetSets,
                        defaultReps = exercise.targetReps,
                        defaultRestSeconds = exercise.restSeconds
                    )
                )
            }
        }
    }

    @Transaction
    suspend fun addMissingDefaults(exercises: List<LibraryExercise>) {
        exercises.forEach { exercise ->
            val cleanName = exercise.name.trim()
            val normalized = cleanName.lowercase()
            if (findByNormalizedName(normalized) == null) {
                insert(exercise.copy(name = cleanName, normalizedName = normalized))
            }
        }
    }
}
