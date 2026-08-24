package com.luis.locogym.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exercise_entries")
data class ExerciseEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exercise: String,
    val weightKg: Double,
    val reps: Int,
    val sets: Int,
    val createdAt: Long = System.currentTimeMillis()
)
