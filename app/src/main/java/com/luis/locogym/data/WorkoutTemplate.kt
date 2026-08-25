package com.luis.locogym.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "workout_templates")
data class WorkoutTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "template_exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutTemplate::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("templateId")]
)
data class TemplateExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val name: String,
    val targetSets: Int,
    val targetReps: Int,
    val position: Int
)

data class TemplateWithExercises(
    @Embedded val template: WorkoutTemplate,
    @Relation(
        parentColumn = "id",
        entityColumn = "templateId"
    )
    val exercises: List<TemplateExercise>
) {
    val orderedExercises: List<TemplateExercise>
        get() = exercises.sortedBy { it.position }
}
