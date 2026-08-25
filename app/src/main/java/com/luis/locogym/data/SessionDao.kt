package com.luis.locogym.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query(
        """SELECT s.id, s.workoutName, s.startedAt, s.completedAt,
            (SELECT COUNT(*) FROM session_exercises e WHERE e.sessionId = s.id) AS exerciseCount,
            (SELECT COUNT(*) FROM session_sets st
                INNER JOIN session_exercises e ON e.id = st.sessionExerciseId
                WHERE e.sessionId = s.id) AS setCount
            FROM workout_sessions s ORDER BY s.completedAt DESC"""
    )
    fun observeHistory(): Flow<List<SessionSummary>>

    @Insert
    suspend fun insertSession(session: WorkoutSession): Long

    @Insert
    suspend fun insertExercise(exercise: SessionExercise): Long

    @Insert
    suspend fun insertSets(sets: List<SessionSet>)

    @Transaction
    suspend fun saveCompletedSession(
        templateId: Long?,
        workoutName: String,
        startedAt: Long,
        completedAt: Long,
        exercises: List<CompletedExerciseInput>
    ) {
        val sessionId = insertSession(
            WorkoutSession(
                templateId = templateId,
                workoutName = workoutName,
                startedAt = startedAt,
                completedAt = completedAt
            )
        )
        exercises.forEach { input ->
            val exerciseId = insertExercise(
                SessionExercise(
                    sessionId = sessionId,
                    name = input.name,
                    plannedWeightKg = input.plannedWeightKg,
                    targetSets = input.targetSets,
                    targetReps = input.targetReps,
                    restSeconds = input.restSeconds,
                    position = input.position
                )
            )
            insertSets(input.sets.mapIndexed { index, set ->
                SessionSet(
                    sessionExerciseId = exerciseId,
                    position = index,
                    weightKg = set.weightKg,
                    reps = set.reps,
                    completedAt = set.completedAt
                )
            })
        }
    }
}
