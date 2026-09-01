package com.luis.locogym

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.luis.locogym.data.ExerciseEntry
import com.luis.locogym.data.CompletedExerciseInput
import com.luis.locogym.data.LocoGymDatabase
import com.luis.locogym.data.TemplateExercise
import com.luis.locogym.data.WorkoutTemplate
import com.luis.locogym.data.WorkoutJsonParser
import com.luis.locogym.data.HistoryExporter
import com.luis.locogym.data.WorkoutAnalytics
import com.luis.locogym.data.LibraryExercise
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LocoGymViewModel(private val database: LocoGymDatabase) : ViewModel() {
    private val _importMessage = MutableStateFlow<String?>(null)
    val importMessage = _importMessage.asStateFlow()
    private val _libraryMessage = MutableStateFlow<String?>(null)
    val libraryMessage = _libraryMessage.asStateFlow()

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

    val exerciseLibrary = database.libraryExerciseDao().observeAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val sessionHistory = database.sessionDao().observeHistory().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val sessionDetails = database.sessionDao().observeAllDetails().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val analytics = sessionDetails.map(WorkoutAnalytics::calculate).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WorkoutAnalytics.calculate(emptyList())
    )

    init {
        viewModelScope.launch {
            database.libraryExerciseDao().addMissingDefaults(
                listOf(
                    LibraryExercise(
                        name = "Bench Press",
                        normalizedName = "bench press",
                        defaultWeightKg = 50.0,
                        defaultSets = 3,
                        defaultReps = 12,
                        defaultRestSeconds = 60
                    ),
                    LibraryExercise(
                        name = "Assisted Chin-Ups",
                        normalizedName = "assisted chin-ups",
                        defaultWeightKg = 25.0,
                        defaultSets = 3,
                        defaultReps = 12,
                        defaultRestSeconds = 60
                    ),
                    LibraryExercise(
                        name = "Horizontal Cable Woodchop",
                        normalizedName = "horizontal cable woodchop",
                        defaultWeightKg = 20.0,
                        defaultSets = 3,
                        defaultReps = 12,
                        defaultRestSeconds = 60
                    )
                )
            )
        }
    }

    fun buildHistoryExport(asJson: Boolean, onReady: (String) -> Unit) {
        viewModelScope.launch {
            val sessions = database.sessionDao().getAllDetails()
            val legacy = database.exerciseDao().getAll()
            onReady(if (asJson) HistoryExporter.toJson(sessions, legacy) else HistoryExporter.toCsv(sessions, legacy))
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            database.withTransaction {
                database.sessionDao().deleteAll()
                database.exerciseDao().deleteAll()
            }
        }
    }

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
            database.libraryExerciseDao().addMissing(exercises)
        }
    }

    fun saveLibraryExercise(exercise: LibraryExercise) {
        viewModelScope.launch {
            runCatching { database.libraryExerciseDao().save(exercise) }
                .onSuccess { _libraryMessage.value = "Exercise saved." }
                .onFailure { error ->
                    _libraryMessage.value = if (error.message?.contains("UNIQUE", true) == true) {
                        "An exercise with that name already exists."
                    } else error.message ?: "Exercise could not be saved."
                }
        }
    }

    fun setLibraryExerciseArchived(exercise: LibraryExercise, archived: Boolean) {
        viewModelScope.launch {
            database.libraryExerciseDao().setArchived(exercise.id, archived)
            _libraryMessage.value = if (archived) "Exercise archived." else "Exercise restored."
        }
    }

    fun clearLibraryMessage() {
        _libraryMessage.value = null
    }

    fun importWorkouts(json: String) {
        viewModelScope.launch {
            runCatching {
                val parsed = WorkoutJsonParser.parse(json)
                database.templateDao().importTemplates(parsed)
                database.libraryExerciseDao().addMissing(parsed.flatMap { it.second })
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
        completedAsPlanned: Boolean,
        exercises: List<CompletedExerciseInput>
    ) {
        viewModelScope.launch {
            database.sessionDao().saveCompletedSession(
                templateId = templateId,
                workoutName = workoutName,
                startedAt = startedAt,
                completedAt = System.currentTimeMillis(),
                completedAsPlanned = completedAsPlanned,
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
