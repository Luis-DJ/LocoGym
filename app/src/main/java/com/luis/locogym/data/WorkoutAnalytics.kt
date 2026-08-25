package com.luis.locogym.data

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

data class PersonalRecord(
    val exerciseName: String,
    val weightKg: Double,
    val reps: Int,
    val achievedAt: Long
)

data class ExerciseProgressPoint(val completedAt: Long, val maxWeightKg: Double)

data class WorkoutAnalytics(
    val monthlyWorkouts: Int,
    val monthlyTrainingDays: Int,
    val monthlySets: Int,
    val monthlyVolumeKg: Double,
    val completedWorkouts: Int,
    val partialWorkouts: Int,
    val weeklyVolumeKg: List<Double>,
    val personalRecords: List<PersonalRecord>,
    val exerciseProgress: Map<String, List<ExerciseProgressPoint>>
) {
    companion object {
        fun calculate(
            sessions: List<SessionWithDetails>,
            now: Long = System.currentTimeMillis(),
            zone: ZoneId = ZoneId.systemDefault()
        ): WorkoutAnalytics {
            val currentMonth = YearMonth.from(Instant.ofEpochMilli(now).atZone(zone))
            val monthly = sessions.filter {
                YearMonth.from(Instant.ofEpochMilli(it.session.completedAt).atZone(zone)) == currentMonth
            }
            fun volume(items: List<SessionWithDetails>) = items.sumOf { session ->
                session.exercises.sumOf { exercise ->
                    exercise.sets.sumOf { (it.weightKg ?: 0.0) * it.reps }
                }
            }
            val weekly = MutableList(5) { 0.0 }
            monthly.forEach { detail ->
                val day = Instant.ofEpochMilli(detail.session.completedAt).atZone(zone).dayOfMonth
                weekly[((day - 1) / 7).coerceAtMost(4)] += volume(listOf(detail))
            }
            val records = linkedMapOf<String, PersonalRecord>()
            val names = linkedMapOf<String, String>()
            val progress = linkedMapOf<String, MutableList<ExerciseProgressPoint>>()
            sessions.sortedBy { it.session.completedAt }.forEach { detail ->
                detail.exercises.forEach { exercise ->
                    val key = exercise.exercise.name.trim().lowercase()
                    val displayName = names.getOrPut(key) { exercise.exercise.name.trim() }
                    val heaviest = exercise.sets.filter { it.weightKg != null }
                        .maxWithOrNull(compareBy<SessionSet> { it.weightKg ?: 0.0 }.thenBy { it.reps })
                    if (heaviest != null) {
                        val candidate = PersonalRecord(displayName, heaviest.weightKg!!, heaviest.reps, heaviest.completedAt)
                        val old = records[key]
                        if (old == null || candidate.weightKg > old.weightKg ||
                            candidate.weightKg == old.weightKg && candidate.reps > old.reps) records[key] = candidate
                        progress.getOrPut(displayName) { mutableListOf() }
                            .add(ExerciseProgressPoint(detail.session.completedAt, heaviest.weightKg))
                    }
                }
            }
            return WorkoutAnalytics(
                monthlyWorkouts = monthly.size,
                monthlyTrainingDays = monthly.map {
                    Instant.ofEpochMilli(it.session.completedAt).atZone(zone).toLocalDate()
                }.distinct().size,
                monthlySets = monthly.sumOf { it.exercises.sumOf { exercise -> exercise.sets.size } },
                monthlyVolumeKg = volume(monthly),
                completedWorkouts = sessions.count { it.session.completedAsPlanned },
                partialWorkouts = sessions.count { !it.session.completedAsPlanned },
                weeklyVolumeKg = weekly,
                personalRecords = records.values.sortedByDescending { it.weightKg }.take(10),
                exerciseProgress = progress.mapValues { it.value.toList() }
            )
        }
    }
}
