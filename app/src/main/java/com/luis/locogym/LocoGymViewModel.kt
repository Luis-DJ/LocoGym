package com.luis.locogym

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.luis.locogym.data.ExerciseEntry
import com.luis.locogym.data.CompletedExerciseInput
import com.luis.locogym.data.LocoGymDatabase
import com.luis.locogym.data.TemplateExercise
import com.luis.locogym.data.WorkoutTemplate
import com.luis.locogym.data.WorkoutJsonParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LocoGymViewModel(private val database: LocoGymDatabase) : ViewModel() {
    private val _importMessage = MutableStateFlow<String?>(null)
    val importMessage = _importMessage.asStateFlow()

    val entries = database.exerciseDao().observeAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val templates = database.templateDao().observeAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val sessionHistory = database.sessionDao().observeHistory().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    fun save(exercise: String, weightKg: Double, reps: Int, sets: Int) {
        viewModelScope.launch {
            database.exerciseDao().insert(
                ExerciseEntry(
                    exercise = exercise.trim(),
                    weightKg = weightKg,
                    reps = reps,
                    sets = sets
                )
            )
        }
    }

    fun saveTemplate(
        existing: WorkoutTemplate?,
        name: String,
        description: String,
        exercises: List<TemplateExercise>
    ) {
        viewModelScope.launch {
            database.templateDao().saveTemplate(
                template = existing?.copy(
                    name = name.trim(),
                    description = description.trim()
                ) ?: WorkoutTemplate(
                    name = name.trim(),
                    description = description.trim()
                ),
                exercises = exercises
            )
        }
    }

    fun importWorkouts(json: String) {
        viewModelScope.launch {
            runCatching {
                val parsed = WorkoutJsonParser.parse(json)
                database.templateDao().importTemplates(parsed)
                parsed.size
            }.onSuccess { count ->
                _importMessage.value = "Imported $count ${if (count == 1) "workout" else "workouts"}."
            }.onFailure { error ->
                _importMessage.value = "Import failed: ${error.message ?: "Invalid JSON file."}"
            }
        }
    }

    fun clearImportMessage() {
        _importMessage.value = null
    }

    fun finishSession(
        templateId: Long?,
        workoutName: String,
        startedAt: Long,
        exercises: List<CompletedExerciseInput>
    ) {
        viewModelScope.launch {
            database.sessionDao().saveCompletedSession(
                templateId = templateId,
                workoutName = workoutName,
                startedAt = startedAt,
                completedAt = System.currentTimeMillis(),
                exercises = exercises
            )
        }
    }

    class Factory(private val database: LocoGymDatabase) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(LocoGymViewModel::class.java))
            return LocoGymViewModel(database) as T
        }
    }
}
