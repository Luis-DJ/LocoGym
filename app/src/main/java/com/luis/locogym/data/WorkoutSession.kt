package com.luis.locogym.data

import androidx.room.Entity
import androidx.room.Embedded
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "workout_sessions")
data class WorkoutSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long?,
    val workoutName: String,
    val startedAt: Long,
    val completedAt: Long,
    val completedAsPlanned: Boolean
)

@Entity(
    tableName = "session_exercises",
    foreignKeys = [ForeignKey(
        entity = WorkoutSession::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("sessionId")]
)
data class SessionExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val name: String,
    val plannedWeightKg: Double?,
    val targetSets: Int,
    val targetReps: Int,
    val restSeconds: Int,
    val position: Int
)

@Entity(
    tableName = "session_sets",
    foreignKeys = [ForeignKey(
        entity = SessionExercise::class,
        parentColumns = ["id"],
        childColumns = ["sessionExerciseId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("sessionExerciseId")]
)
data class SessionSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionExerciseId: Long,
    val position: Int,
    val weightKg: Double?,
    val reps: Int,
    val completedAt: Long
)

data class CompletedSetInput(val weightKg: Double?, val reps: Int, val completedAt: Long)

data class CompletedExerciseInput(
    val name: String,
    val plannedWeightKg: Double?,
    val targetSets: Int,
    val targetReps: Int,
    val restSeconds: Int,
    val position: Int,
    val sets: List<CompletedSetInput>
)

data class SessionSummary(
    val id: Long,
    val workoutName: String,
    val startedAt: Long,
    val completedAt: Long,
    val completedAsPlanned: Boolean,
    val exerciseCount: Int,
    val setCount: Int
)

data class SessionExerciseWithSets(
    @Embedded val exercise: SessionExercise,
    @Relation(
        parentColumn = "id",
        entityColumn = "sessionExerciseId"
    )
    val sets: List<SessionSet>
) {
    val orderedSets: List<SessionSet> get() = sets.sortedBy { it.position }
}

data class SessionWithDetails(
    @Embedded val session: WorkoutSession,
    @Relation(
        entity = SessionExercise::class,
        parentColumn = "id",
        entityColumn = "sessionId"
    )
    val exercises: List<SessionExerciseWithSets>
) {
    val orderedExercises: List<SessionExerciseWithSets>
        get() = exercises.sortedBy { it.exercise.position }
}
