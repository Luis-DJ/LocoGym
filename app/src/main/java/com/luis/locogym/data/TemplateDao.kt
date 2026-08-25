package com.luis.locogym.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateDao {
    @Transaction
    @Query("SELECT * FROM workout_templates ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<TemplateWithExercises>>

    @Insert
    suspend fun insertTemplate(template: WorkoutTemplate): Long

    @Update
    suspend fun updateTemplate(template: WorkoutTemplate)

    @Insert
    suspend fun insertExercises(exercises: List<TemplateExercise>)

    @Query("DELETE FROM template_exercises WHERE templateId = :templateId")
    suspend fun deleteExercises(templateId: Long)

    @Query("SELECT id FROM workout_templates WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findTemplateIdByName(name: String): Long?

    @Transaction
    suspend fun saveTemplate(
        template: WorkoutTemplate,
        exercises: List<TemplateExercise>
    ) {
        val templateId = if (template.id == 0L) {
            insertTemplate(template)
        } else {
            updateTemplate(template)
            deleteExercises(template.id)
            template.id
        }

        insertExercises(
            exercises.mapIndexed { index, exercise ->
                exercise.copy(id = 0, templateId = templateId, position = index)
            }
        )
    }

    @Transaction
    suspend fun importTemplates(items: List<Pair<WorkoutTemplate, List<TemplateExercise>>>) {
        items.forEach { (template, exercises) ->
            require(findTemplateIdByName(template.name) == null) {
                "A workout named '${template.name}' already exists."
            }
            saveTemplate(template, exercises)
        }
    }
}
