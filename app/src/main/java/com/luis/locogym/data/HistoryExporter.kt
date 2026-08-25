package com.luis.locogym.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

object HistoryExporter {
    fun toJson(
        sessions: List<SessionWithDetails>,
        legacyEntries: List<ExerciseEntry>,
        exportedAt: Long = System.currentTimeMillis()
    ): String = JSONObject().apply {
        put("formatVersion", 1)
        put("exportedAt", Instant.ofEpochMilli(exportedAt).toString())
        put("sessions", JSONArray().apply {
            sessions.forEach { detail ->
                put(JSONObject().apply {
                    put("workoutName", detail.session.workoutName)
                    put("status", if (detail.session.completedAsPlanned) "completed" else "partial")
                    put("startedAt", Instant.ofEpochMilli(detail.session.startedAt).toString())
                    put("completedAt", Instant.ofEpochMilli(detail.session.completedAt).toString())
                    put("exercises", JSONArray().apply {
                        detail.orderedExercises.forEach { item ->
                            put(JSONObject().apply {
                                put("name", item.exercise.name)
                                putNullable("plannedWeightKg", item.exercise.plannedWeightKg)
                                put("targetSets", item.exercise.targetSets)
                                put("targetReps", item.exercise.targetReps)
                                put("restSeconds", item.exercise.restSeconds)
                                put("sets", JSONArray().apply {
                                    item.orderedSets.forEach { set ->
                                        put(JSONObject().apply {
                                            put("set", set.position + 1)
                                            putNullable("weightKg", set.weightKg)
                                            put("reps", set.reps)
                                            put("completedAt", Instant.ofEpochMilli(set.completedAt).toString())
                                        })
                                    }
                                })
                            })
                        }
                    })
                })
            }
        })
        put("legacyQuickRecords", JSONArray().apply {
            legacyEntries.forEach { entry ->
                put(JSONObject().apply {
                    put("exercise", entry.exercise)
                    put("weightKg", entry.weightKg)
                    put("reps", entry.reps)
                    put("sets", entry.sets)
                    put("createdAt", Instant.ofEpochMilli(entry.createdAt).toString())
                })
            }
        })
    }.toString(2)

    fun toCsv(
        sessions: List<SessionWithDetails>,
        legacyEntries: List<ExerciseEntry>
    ): String = buildString {
        appendLine("record_type,session_started,session_completed,workout,status,exercise,set,planned_weight_kg,actual_weight_kg,target_reps,actual_reps,rest_seconds")
        sessions.forEach { detail ->
            detail.orderedExercises.forEach { item ->
                item.orderedSets.forEach { set ->
                    appendCsvRow(
                        "session",
                        Instant.ofEpochMilli(detail.session.startedAt).toString(),
                        Instant.ofEpochMilli(detail.session.completedAt).toString(),
                        detail.session.workoutName,
                        if (detail.session.completedAsPlanned) "completed" else "partial",
                        item.exercise.name,
                        (set.position + 1).toString(),
                        item.exercise.plannedWeightKg?.toString().orEmpty(),
                        set.weightKg?.toString().orEmpty(),
                        item.exercise.targetReps.toString(),
                        set.reps.toString(),
                        item.exercise.restSeconds.toString()
                    )
                }
            }
        }
        legacyEntries.forEach { entry ->
            repeat(entry.sets) { setIndex ->
                appendCsvRow(
                    "legacy",
                    "",
                    Instant.ofEpochMilli(entry.createdAt).toString(),
                    "Quick record",
                    "completed",
                    entry.exercise,
                    (setIndex + 1).toString(),
                    entry.weightKg.toString(),
                    entry.weightKg.toString(),
                    entry.reps.toString(),
                    entry.reps.toString(),
                    ""
                )
            }
        }
    }

    private fun JSONObject.putNullable(name: String, value: Double?) {
        put(name, value ?: JSONObject.NULL)
    }

    private fun StringBuilder.appendCsvRow(vararg values: String) {
        appendLine(values.joinToString(",") { value ->
            "\"${value.replace("\"", "\"\"")}\""
        })
    }
}
