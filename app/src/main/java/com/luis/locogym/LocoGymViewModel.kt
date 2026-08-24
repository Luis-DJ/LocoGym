package com.luis.locogym

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.luis.locogym.data.ExerciseDao
import com.luis.locogym.data.ExerciseEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LocoGymViewModel(private val dao: ExerciseDao) : ViewModel() {
    val entries = dao.observeAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    fun save(exercise: String, weightKg: Double, reps: Int, sets: Int) {
        viewModelScope.launch {
            dao.insert(
                ExerciseEntry(
                    exercise = exercise.trim(),
                    weightKg = weightKg,
                    reps = reps,
                    sets = sets
                )
            )
        }
    }

    class Factory(private val dao: ExerciseDao) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(LocoGymViewModel::class.java))
            return LocoGymViewModel(dao) as T
        }
    }
}
