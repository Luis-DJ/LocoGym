package com.luis.locogym.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "library_exercises",
    indices = [Index(value = ["normalizedName"], unique = true)]
)
data class LibraryExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val normalizedName: String = name.trim().lowercase(),
    val defaultWeightKg: Double?,
    val defaultSets: Int,
    val defaultReps: Int,
    val defaultRestSeconds: Int,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
