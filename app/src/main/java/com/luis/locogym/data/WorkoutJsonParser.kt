package com.luis.locogym.data

import org.json.JSONObject

object WorkoutJsonParser {
    fun parse(json: String): List<Pair<WorkoutTemplate, List<TemplateExercise>>> {
        val root = JSONObject(json)
        require(root.optInt("formatVersion", -1) == 1) { "Unsupported or missing formatVersion." }
        val templatesJson = root.optJSONArray("workoutTemplates")
            ?: throw IllegalArgumentException("workoutTemplates must be an array.")
        require(templatesJson.length() > 0) { "The file contains no workouts." }

        return (0 until templatesJson.length()).map { templateIndex ->
            val item = templatesJson.getJSONObject(templateIndex)
            val name = item.optString("name").trim()
            require(name.isNotEmpty()) { "Workout ${templateIndex + 1} has no name." }
            val exercisesJson = item.optJSONArray("exercises")
                ?: throw IllegalArgumentException("$name has no exercises array.")
            require(exercisesJson.length() > 0) { "$name has no exercises." }

            val exercises = (0 until exercisesJson.length()).map { exerciseIndex ->
                val exercise = exercisesJson.getJSONObject(exerciseIndex)
                val exerciseName = exercise.optString("name").trim()
                val sets = exercise.optInt("targetSets", 0)
                val reps = exercise.optInt("targetReps", 0)
                val rest = exercise.optInt("restSeconds", 0)
                val weight = if (exercise.isNull("targetWeightKg")) null
                    else exercise.optDouble("targetWeightKg", Double.NaN)

                require(exerciseName.isNotEmpty()) { "$name exercise ${exerciseIndex + 1} has no name." }
                require(weight == null || (weight.isFinite() && weight >= 0)) {
                    "$exerciseName has an invalid targetWeightKg."
                }
                require(sets > 0) { "$exerciseName must have at least one set." }
                require(reps > 0) { "$exerciseName must have at least one rep." }
                require(rest in 1..3600) { "$exerciseName restSeconds must be between 1 and 3600." }

                TemplateExercise(
                    templateId = 0,
                    name = exerciseName,
                    targetWeightKg = weight,
                    targetSets = sets,
                    targetReps = reps,
                    restSeconds = rest,
                    position = exerciseIndex
                )
            }
            WorkoutTemplate(
                name = name,
                description = item.optString("description").trim()
            ) to exercises
        }.also { parsed ->
            val normalizedNames = parsed.map { it.first.name.lowercase() }
            require(normalizedNames.distinct().size == normalizedNames.size) {
                "The file contains duplicate workout names."
            }
        }
    }
}
