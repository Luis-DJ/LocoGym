package com.luis.locogym

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.luis.locogym.data.ExerciseEntry
import com.luis.locogym.data.LocoGymDatabase
import com.luis.locogym.data.TemplateExercise
import com.luis.locogym.data.WorkoutTemplate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LocoGymViewModel(private val database: LocoGymDatabase) : ViewModel() {
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

    class Factory(private val database: LocoGymDatabase) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(LocoGymViewModel::class.java))
            return LocoGymViewModel(database) as T
        }
    }
}
