package com.luis.locogym.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luis.locogym.LocoGymViewModel
import com.luis.locogym.R
import com.luis.locogym.data.ExerciseEntry
import com.luis.locogym.data.CompletedExerciseInput
import com.luis.locogym.data.CompletedSetInput
import com.luis.locogym.data.SessionSummary
import com.luis.locogym.data.TemplateExercise
import com.luis.locogym.data.TemplateWithExercises
import com.luis.locogym.data.WorkoutAnalytics
import com.luis.locogym.data.LibraryExercise
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private enum class HomeSection { TEMPLATES, LIBRARY, HISTORY, PROGRESS }
private enum class AlertMode { SOUND, VIBRATION, BOTH }

private data class ExerciseDraft(
    val name: String = "",
    val weightKg: String = "",
    val sets: String = "3",
    val reps: String = "8",
    val restSeconds: String = "60"
)

private data class SetRun(
    val weightKg: String,
    val reps: String,
    val completedAt: Long? = null
)

private data class ExerciseRun(
    val exercise: TemplateExercise,
    val sets: List<SetRun>
)

private data class ActiveWorkoutDraft(
    val templateId: Long,
    val startedAt: Long,
    val setsByExercise: List<List<SetRun>>
)

private const val ACTIVE_WORKOUT_DRAFT_KEY = "active_workout_draft_v1"

private fun newActiveWorkoutDraft(workout: TemplateWithExercises): ActiveWorkoutDraft =
    ActiveWorkoutDraft(
        templateId = workout.template.id,
        startedAt = System.currentTimeMillis(),
        setsByExercise = workout.orderedExercises.map { exercise ->
            List(exercise.targetSets) {
                SetRun(
                    weightKg = exercise.targetWeightKg?.clean().orEmpty(),
                    reps = exercise.targetReps.toString()
                )
            }
        }
    )

private fun ActiveWorkoutDraft.toJson(): String = JSONObject().apply {
    put("templateId", templateId)
    put("startedAt", startedAt)
    put("exercises", JSONArray().apply {
        setsByExercise.forEach { sets ->
            put(JSONArray().apply {
                sets.forEach { set ->
                    put(JSONObject().apply {
                        put("weightKg", set.weightKg)
                        put("reps", set.reps)
                        put("completedAt", set.completedAt ?: JSONObject.NULL)
                    })
                }
            })
        }
    })
}.toString()

private fun readActiveWorkoutDraft(preferences: android.content.SharedPreferences): ActiveWorkoutDraft? =
    runCatching {
        val root = JSONObject(preferences.getString(ACTIVE_WORKOUT_DRAFT_KEY, null) ?: return null)
        val exercises = root.getJSONArray("exercises")
        ActiveWorkoutDraft(
            templateId = root.getLong("templateId"),
            startedAt = root.getLong("startedAt"),
            setsByExercise = List(exercises.length()) { exerciseIndex ->
                val sets = exercises.getJSONArray(exerciseIndex)
                List(sets.length()) { setIndex ->
                    val set = sets.getJSONObject(setIndex)
                    SetRun(
                        weightKg = set.optString("weightKg"),
                        reps = set.optString("reps"),
                        completedAt = if (set.isNull("completedAt")) null else set.getLong("completedAt")
                    )
                }
            }
        )
    }.getOrNull()

@Composable
fun LocoGymApp(viewModel: LocoGymViewModel) {
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val exerciseLibrary by viewModel.exerciseLibrary.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val sessionHistory by viewModel.sessionHistory.collectAsStateWithLifecycle()
    val analytics by viewModel.analytics.collectAsStateWithLifecycle()
    val importMessage by viewModel.importMessage.collectAsStateWithLifecycle()
    val libraryMessage by viewModel.libraryMessage.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("locogym_settings", Context.MODE_PRIVATE) }
    var alertMode by rememberSaveable {
        mutableStateOf(
            runCatching { AlertMode.valueOf(preferences.getString("alert_mode", AlertMode.BOTH.name)!!) }
                .getOrDefault(AlertMode.BOTH)
        )
    }
    var showAlertSettings by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("The selected file could not be read.")
        }.onSuccess(viewModel::importWorkouts)
            .onFailure { viewModel.importWorkouts("") }
    }
    var pendingExport by remember { mutableStateOf("") }
    val csvExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let { context.contentResolver.openOutputStream(it)?.bufferedWriter()?.use { writer -> writer.write(pendingExport) } } }
    val jsonExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { context.contentResolver.openOutputStream(it)?.bufferedWriter()?.use { writer -> writer.write(pendingExport) } } }
    var section by rememberSaveable { mutableStateOf(HomeSection.TEMPLATES) }
    var editing by remember { mutableStateOf<TemplateWithExercises?>(null) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var activeWorkout by remember { mutableStateOf<TemplateWithExercises?>(null) }
    var activeDraft by remember { mutableStateOf(readActiveWorkoutDraft(preferences)) }
    var viewedWorkout by remember { mutableStateOf<TemplateWithExercises?>(null) }

    fun saveActiveDraft(draft: ActiveWorkoutDraft?) {
        activeDraft = draft
        if (draft == null) preferences.edit().remove(ACTIVE_WORKOUT_DRAFT_KEY).commit()
        else preferences.edit().putString(ACTIVE_WORKOUT_DRAFT_KEY, draft.toJson()).commit()
    }

    LaunchedEffect(templates, activeDraft?.templateId) {
        if (activeWorkout == null && activeDraft != null) {
            activeWorkout = templates.firstOrNull { it.template.id == activeDraft!!.templateId }
        }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (activeWorkout != null) {
                ActiveWorkoutScreen(
                    workout = activeWorkout!!,
                    alertMode = alertMode,
                    restoredDraft = activeDraft,
                    onDraftChange = ::saveActiveDraft,
                    onCancel = {
                        saveActiveDraft(null)
                        activeWorkout = null
                    },
                    onFinish = { startedAt, completed, completedAsPlanned ->
                        viewModel.finishSession(
                            templateId = activeWorkout!!.template.id,
                            workoutName = activeWorkout!!.template.name,
                            startedAt = startedAt,
                            completedAsPlanned = completedAsPlanned,
                            exercises = completed
                        )
                        saveActiveDraft(null)
                        activeWorkout = null
                        section = HomeSection.HISTORY
                    }
                )
            } else if (editorOpen) {
                TemplateEditor(
                    existing = editing,
                    exerciseLibrary = exerciseLibrary,
                    onCancel = { editorOpen = false },
                    onSave = { name, description, exercises ->
                        viewModel.saveTemplate(editing?.template, name, description, exercises)
                        editorOpen = false
                    }
                )
            } else if (viewedWorkout != null) {
                WorkoutDetailScreen(
                    workout = viewedWorkout!!,
                    onBack = { viewedWorkout = null },
                    onStart = {
                        val workout = viewedWorkout!!
                        saveActiveDraft(newActiveWorkoutDraft(workout))
                        activeWorkout = workout
                    },
                    onEdit = {
                        editing = viewedWorkout
                        viewedWorkout = null
                        editorOpen = true
                    }
                )
            } else {
                HomeScreen(
                    section = section,
                    onSectionChange = { section = it },
                    templates = templates,
                    exerciseLibrary = exerciseLibrary,
                    entries = entries,
                    sessionHistory = sessionHistory,
                    analytics = analytics,
                    onNewTemplate = { editing = null; editorOpen = true },
                    onOpenTemplate = { viewedWorkout = it },
                    onImportTemplates = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                    importMessage = importMessage,
                    onDismissImportMessage = viewModel::clearImportMessage,
                    onAlertSettings = { showAlertSettings = true },
                    onExportCsv = {
                        viewModel.buildHistoryExport(false) { content ->
                            pendingExport = content
                            csvExportLauncher.launch("locogym-history.csv")
                        }
                    },
                    onExportJson = {
                        viewModel.buildHistoryExport(true) { content ->
                            pendingExport = content
                            jsonExportLauncher.launch("locogym-history.json")
                        }
                    },
                    onClearHistory = viewModel::clearHistory,
                    libraryMessage = libraryMessage,
                    onSaveLibraryExercise = viewModel::saveLibraryExercise,
                    onSetLibraryArchived = viewModel::setLibraryExerciseArchived,
                    onDismissLibraryMessage = viewModel::clearLibraryMessage
                )
            }
        }
        if (showAlertSettings) {
            AlertSettingsDialog(
                selected = alertMode,
                onSelect = { mode ->
                    alertMode = mode
                    preferences.edit().putString("alert_mode", mode.name).apply()
                },
                onTest = { scope.launch { playRestAlert(context, alertMode) } },
                onDismiss = { showAlertSettings = false }
            )
        }
    }
}

@Composable
private fun HomeScreen(
    section: HomeSection,
    onSectionChange: (HomeSection) -> Unit,
    templates: List<TemplateWithExercises>,
    exerciseLibrary: List<LibraryExercise>,
    entries: List<ExerciseEntry>,
    sessionHistory: List<SessionSummary>,
    analytics: WorkoutAnalytics,
    onNewTemplate: () -> Unit,
    onOpenTemplate: (TemplateWithExercises) -> Unit,
    onImportTemplates: () -> Unit,
    importMessage: String?,
    onDismissImportMessage: () -> Unit,
    onAlertSettings: () -> Unit,
    onExportCsv: () -> Unit,
    onExportJson: () -> Unit,
    onClearHistory: () -> Unit,
    libraryMessage: String?,
    onSaveLibraryExercise: (LibraryExercise) -> Unit,
    onSetLibraryArchived: (LibraryExercise, Boolean) -> Unit,
    onDismissLibraryMessage: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(36.dp))
        Text("LocoGym", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "No account. No cloud. No nonsense.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onAlertSettings) { Text("Timer alert") }
        }
        Spacer(Modifier.height(18.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
            FilterChip(
                selected = section == HomeSection.TEMPLATES,
                onClick = { onSectionChange(HomeSection.TEMPLATES) },
                label = { Text("My Workouts") }
            )
            }
            item {
                FilterChip(
                    selected = section == HomeSection.LIBRARY,
                    onClick = { onSectionChange(HomeSection.LIBRARY) },
                    label = { Text("Exercises") }
                )
            }
            item {
            FilterChip(
                selected = section == HomeSection.HISTORY,
                onClick = { onSectionChange(HomeSection.HISTORY) },
                label = { Text("History") }
            )
            }
            item {
            FilterChip(
                selected = section == HomeSection.PROGRESS,
                onClick = { onSectionChange(HomeSection.PROGRESS) },
                label = { Text("Progress") }
            )
            }
        }
        Spacer(Modifier.height(12.dp))
        when (section) {
            HomeSection.TEMPLATES -> TemplateList(
                templates = templates,
                onNewTemplate = onNewTemplate,
                onOpenTemplate = onOpenTemplate,
                onImportTemplates = onImportTemplates,
                importMessage = importMessage,
                onDismissImportMessage = onDismissImportMessage,
                modifier = Modifier.weight(1f)
            )
            HomeSection.HISTORY -> HistoryScreen(
                sessionHistory = sessionHistory,
                legacyEntries = entries,
                onExportCsv = onExportCsv,
                onExportJson = onExportJson,
                onClearHistory = onClearHistory,
                modifier = Modifier.weight(1f)
            )
            HomeSection.LIBRARY -> ExerciseLibraryScreen(
                exercises = exerciseLibrary,
                message = libraryMessage,
                onSave = onSaveLibraryExercise,
                onSetArchived = onSetLibraryArchived,
                onDismissMessage = onDismissLibraryMessage,
                modifier = Modifier.weight(1f)
            )
            HomeSection.PROGRESS -> ProgressScreen(analytics, Modifier.weight(1f))
        }
        Text("v0.9.0-beta01 • stored only on this device", style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ExerciseLibraryScreen(
    exercises: List<LibraryExercise>,
    message: String?,
    onSave: (LibraryExercise) -> Unit,
    onSetArchived: (LibraryExercise, Boolean) -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    var search by rememberSaveable { mutableStateOf("") }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<LibraryExercise?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    val visible = exercises.filter {
        (showArchived || !it.archived) && it.name.contains(search.trim(), ignoreCase = true)
    }
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Exercise Library", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Your exercises and reusable defaults",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(onClick = { creating = true }) { Text("New") }
            }
        }
        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("Search exercises") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = showArchived, onCheckedChange = { showArchived = it })
                Text("Show archived")
            }
        }
        if (message != null) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(message, modifier = Modifier.weight(1f))
                        TextButton(onClick = onDismissMessage) { Text("Dismiss") }
                    }
                }
            }
        }
        if (visible.isEmpty()) {
            item { Text(if (search.isBlank()) "No exercises here yet." else "No matching exercises.") }
        }
        items(visible, key = { it.id }) { exercise ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WorkoutImage(
                        resourceId = exerciseImageResource(exercise.name),
                        contentDescription = "${exercise.name} illustration",
                        fallbackText = exercise.name.take(1).uppercase(),
                        modifier = Modifier.size(64.dp)
                    )
                    Column(Modifier.weight(1f)) {
                        Text(exercise.name, fontWeight = FontWeight.SemiBold)
                        val weight = exercise.defaultWeightKg?.let { " @ ${it.clean()} kg" }.orEmpty()
                        Text("${exercise.defaultSets} × ${exercise.defaultReps}$weight • ${exercise.defaultRestSeconds}s")
                        if (exercise.archived) Text("Archived", color = MaterialTheme.colorScheme.tertiary)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        TextButton(onClick = { editing = exercise }) { Text("Edit") }
                        TextButton(onClick = { onSetArchived(exercise, !exercise.archived) }) {
                            Text(if (exercise.archived) "Restore" else "Archive")
                        }
                    }
                }
            }
        }
    }
    if (creating || editing != null) {
        LibraryExerciseDialog(
            existing = editing,
            onDismiss = { creating = false; editing = null },
            onSave = {
                onSave(it)
                creating = false
                editing = null
            }
        )
    }
}

@Composable
private fun LibraryExerciseDialog(
    existing: LibraryExercise?,
    onDismiss: () -> Unit,
    onSave: (LibraryExercise) -> Unit
) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var weight by remember(existing?.id) { mutableStateOf(existing?.defaultWeightKg?.clean().orEmpty()) }
    var sets by remember(existing?.id) { mutableStateOf(existing?.defaultSets?.toString() ?: "3") }
    var reps by remember(existing?.id) { mutableStateOf(existing?.defaultReps?.toString() ?: "10") }
    var rest by remember(existing?.id) { mutableStateOf(existing?.defaultRestSeconds?.toString() ?: "60") }
    val valid = name.isNotBlank() && (weight.isBlank() || weight.toDoubleOrNull()?.let { it >= 0 } == true) &&
        sets.toIntOrNull()?.let { it > 0 } == true && reps.toIntOrNull()?.let { it > 0 } == true &&
        rest.toIntOrNull()?.let { it in 1..3600 } == true
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New exercise" else "Edit exercise") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Exercise name") }, singleLine = true)
                DecimalField("Default weight kg (optional)", weight, { weight = it }, Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IntegerField("Sets", sets, { sets = it }, Modifier.weight(1f))
                    IntegerField("Reps", reps, { reps = it }, Modifier.weight(1f))
                }
                IntegerField("Rest seconds", rest, { rest = it }, Modifier.fillMaxWidth())
                Text(
                    "Defaults save typing. Each workout can override them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = {
                    onSave(
                        (existing ?: LibraryExercise(
                            name = name,
                            defaultWeightKg = null,
                            defaultSets = 3,
                            defaultReps = 10,
                            defaultRestSeconds = 60
                        )).copy(
                            name = name.trim(),
                            normalizedName = name.trim().lowercase(),
                            defaultWeightKg = weight.toDoubleOrNull(),
                            defaultSets = sets.toInt(),
                            defaultReps = reps.toInt(),
                            defaultRestSeconds = rest.toInt()
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun TemplateList(
    templates: List<TemplateWithExercises>,
    onNewTemplate: () -> Unit,
    onOpenTemplate: (TemplateWithExercises) -> Unit,
    onImportTemplates: () -> Unit,
    importMessage: String?,
    onDismissImportMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onNewTemplate, modifier = Modifier.weight(1f)) { Text("Create workout") }
                OutlinedButton(onClick = onImportTemplates, modifier = Modifier.weight(1f)) { Text("Import JSON") }
            }
        }
        if (importMessage != null) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(importMessage, modifier = Modifier.weight(1f))
                        TextButton(onClick = onDismissImportMessage) { Text("Dismiss") }
                    }
                }
            }
        }
        if (templates.isEmpty()) {
            item {
                Text(
                    "Plan your first workout before you go to the gym.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(templates, key = { it.template.id }) { item ->
            Card(onClick = { onOpenTemplate(item) }, modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WorkoutImage(
                        resourceId = workoutCoverResource(item.template.name),
                        contentDescription = "${item.template.name} workout illustration",
                        modifier = Modifier.size(104.dp)
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            item.template.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            item.template.description.ifBlank { "Reusable workout plan" },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                        Text(
                            "${item.exercises.size} ${if (item.exercises.size == 1) "exercise" else "exercises"}",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    Text("›", style = MaterialTheme.typography.headlineMedium)
                }
            }
        }
    }
}

@Composable
private fun WorkoutDetailScreen(
    workout: TemplateWithExercises,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onEdit: () -> Unit
) {
    var openedExercise by remember { mutableStateOf<TemplateExercise?>(null) }
    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(30.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text("‹ My Workouts") }
            TextButton(onClick = onEdit) { Text("Edit") }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                WorkoutImage(
                    resourceId = workoutCoverResource(workout.template.name),
                    contentDescription = "${workout.template.name} workout illustration",
                    modifier = Modifier.fillMaxWidth().height(210.dp)
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    workout.template.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                if (workout.template.description.isNotBlank()) {
                    Text(
                        workout.template.description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "${workout.exercises.size} ${if (workout.exercises.size == 1) "exercise" else "exercises"}",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                    Text("Start workout")
                }
                Spacer(Modifier.height(8.dp))
                Text("Exercises", style = MaterialTheme.typography.titleLarge)
            }
            items(workout.orderedExercises, key = { it.id }) { exercise ->
                Card(onClick = { openedExercise = exercise }, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WorkoutImage(
                            resourceId = exerciseImageResource(exercise.name),
                            contentDescription = "${exercise.name} exercise illustration",
                            fallbackText = exercise.name.take(1).uppercase(),
                            modifier = Modifier.size(82.dp)
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(exercise.name, fontWeight = FontWeight.SemiBold)
                            val weight = exercise.targetWeightKg?.let { " @ ${it.clean()} kg" }.orEmpty()
                            Text("${exercise.targetSets} × ${exercise.targetReps}$weight")
                            Text(
                                "${exercise.restSeconds}s rest",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text("›", style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
    openedExercise?.let { exercise ->
        ExercisePreviewDialog(exercise = exercise, onDismiss = { openedExercise = null })
    }
}

@Composable
private fun ExercisePreviewDialog(exercise: TemplateExercise, onDismiss: () -> Unit) {
    val isPallof = exercise.name.contains("pallof", ignoreCase = true)
    var extended by remember { mutableStateOf(false) }
    LaunchedEffect(isPallof) {
        if (isPallof) {
            while (true) {
                delay(if (extended) 1_200 else 900)
                extended = !extended
            }
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(exercise.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Crossfade(
                    targetState = isPallof && extended,
                    label = "Pallof demonstration"
                ) { showExtended ->
                    WorkoutImage(
                        resourceId = if (isPallof) {
                            if (showExtended) R.drawable.exercise_robot_pallof_extended
                            else R.drawable.exercise_robot_pallof_start
                        } else exerciseImageResource(exercise.name),
                        contentDescription = "${exercise.name} demonstration",
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                    )
                }
                if (isPallof) {
                    Text(
                        if (extended) "Hold — resist rotation" else "Start at the chest",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                val weight = exercise.targetWeightKg?.let { " @ ${it.clean()} kg" }.orEmpty()
                Text("Target ${exercise.targetSets} × ${exercise.targetReps}$weight • ${exercise.restSeconds}s rest")
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Close") }
            }
        }
    }
}

@Composable
private fun WorkoutImage(
    resourceId: Int?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    fallbackText: String = "•"
) {
    val shape = RoundedCornerShape(16.dp)
    if (resourceId != null) {
        Image(
            painter = painterResource(resourceId),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(shape)
        )
    } else {
        Box(
            modifier = modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                fallbackText,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

private fun workoutCoverResource(name: String): Int = when {
    name.contains("upper", ignoreCase = true) -> R.drawable.cover_upper_body
    name.contains("leg", ignoreCase = true) || name.contains("lower", ignoreCase = true) -> R.drawable.cover_legs
    else -> R.drawable.cover_full_body
}

private fun exerciseImageResource(name: String): Int? = when {
    name.equals("Bench Press", ignoreCase = true) -> R.drawable.exercise_robot_barbell_bench_press
    name.contains("assisted chin", ignoreCase = true) -> R.drawable.exercise_robot_assisted_chin_up
    name.contains("woodchop", ignoreCase = true) || name.contains("wood chop", ignoreCase = true) ->
        R.drawable.exercise_robot_horizontal_woodchop
    name.contains("shoulder press", ignoreCase = true) -> R.drawable.exercise_robot_shoulder_press
    name.contains("lat pulldown", ignoreCase = true) -> R.drawable.exercise_robot_lat_pulldown
    name.contains("bench press", ignoreCase = true) -> R.drawable.exercise_mature_bench_press
    name.contains("seated row", ignoreCase = true) -> R.drawable.exercise_mature_seated_row
    name.contains("chest fly", ignoreCase = true) && name.contains("machine", ignoreCase = true) -> R.drawable.exercise_machine_chest_fly
    name.contains("chest fly", ignoreCase = true) -> R.drawable.exercise_dumbbell_chest_fly
    name.contains("tricep pushdown", ignoreCase = true) -> R.drawable.exercise_triceps_pushdown
    name.contains("flat bar biceps", ignoreCase = true) -> R.drawable.exercise_cable_biceps_curl
    name.contains("pallof", ignoreCase = true) -> R.drawable.exercise_robot_pallof_extended
    name.contains("leg curl", ignoreCase = true) -> R.drawable.exercise_leg_curl
    name.contains("leg extension", ignoreCase = true) -> R.drawable.exercise_leg_extension
    name.contains("incline bicep", ignoreCase = true) -> R.drawable.exercise_incline_biceps_curl
    name.contains("lounge squat", ignoreCase = true) || name.contains("lunge squat", ignoreCase = true) -> R.drawable.exercise_lunge_squat
    name.contains("calf raise", ignoreCase = true) -> R.drawable.exercise_leg_press_calf_raise
    name.contains("bosu", ignoreCase = true) && name.contains("leg raise", ignoreCase = true) -> R.drawable.exercise_bosu_leg_raise
    name.contains("kneeling crunch", ignoreCase = true) -> R.drawable.exercise_cable_kneeling_crunch
    name.contains("leg press", ignoreCase = true) -> R.drawable.exercise_robot_leg_press
    else -> null
}

@Composable
private fun TemplateEditor(
    existing: TemplateWithExercises?,
    exerciseLibrary: List<LibraryExercise>,
    onCancel: () -> Unit,
    onSave: (String, String, List<TemplateExercise>) -> Unit
) {
    var libraryOpen by rememberSaveable(existing?.template?.id) { mutableStateOf(false) }
    var name by remember(existing?.template?.id) { mutableStateOf(existing?.template?.name.orEmpty()) }
    var description by remember(existing?.template?.id) { mutableStateOf(existing?.template?.description.orEmpty()) }
    var exercises by remember(existing?.template?.id) {
        mutableStateOf(existing?.orderedExercises?.map {
            ExerciseDraft(
                name = it.name,
                weightKg = it.targetWeightKg?.clean().orEmpty(),
                sets = it.targetSets.toString(),
                reps = it.targetReps.toString(),
                restSeconds = it.restSeconds.toString()
            )
        }.orEmpty())
    }
    val valid = name.isNotBlank() && exercises.isNotEmpty() && exercises.all {
        it.name.isNotBlank() && (it.weightKg.isBlank() || (it.weightKg.toDoubleOrNull() ?: -1.0) >= 0) &&
            (it.sets.toIntOrNull() ?: 0) > 0 && (it.reps.toIntOrNull() ?: 0) > 0 &&
            (it.restSeconds.toIntOrNull() ?: 0) in 1..3600
    }

    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(36.dp))
        Text(
            if (existing == null) "New workout" else "Edit workout",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Workout name") },
                    placeholder = { Text("Upper Body") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    placeholder = { Text("Chest, back, shoulders and arms") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item { Text("Exercises", style = MaterialTheme.typography.titleLarge) }
            itemsIndexed(exercises) { index, draft ->
                ExerciseDraftEditor(
                    number = index + 1,
                    draft = draft,
                    onChange = { updated -> exercises = exercises.toMutableList().also { it[index] = updated } },
                    onRemove = { exercises = exercises.toMutableList().also { it.removeAt(index) } }
                )
            }
            item {
                Button(
                    onClick = { libraryOpen = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Add from Exercise Library") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(
                enabled = valid,
                onClick = {
                    onSave(name, description, exercises.mapIndexed { index, draft ->
                        TemplateExercise(
                            templateId = existing?.template?.id ?: 0,
                            name = draft.name.trim(),
                            targetWeightKg = draft.weightKg.toDoubleOrNull(),
                            targetSets = draft.sets.toInt(),
                            targetReps = draft.reps.toInt(),
                            restSeconds = draft.restSeconds.toInt(),
                            position = index
                        )
                    })
                },
                modifier = Modifier.weight(1f)
            ) { Text("Save workout") }
        }
        Spacer(Modifier.height(20.dp))
    }
    if (libraryOpen) {
        AddFromLibraryDialog(
            exercises = exerciseLibrary.filter { !it.archived },
            alreadyAdded = exercises.map { it.name.trim().lowercase() }.toSet(),
            onAdd = { item ->
                exercises = exercises + ExerciseDraft(
                    name = item.name,
                    weightKg = item.defaultWeightKg?.clean().orEmpty(),
                    sets = item.defaultSets.toString(),
                    reps = item.defaultReps.toString(),
                    restSeconds = item.defaultRestSeconds.toString()
                )
            },
            onDismiss = { libraryOpen = false }
        )
    }
}

@Composable
private fun AddFromLibraryDialog(
    exercises: List<LibraryExercise>,
    alreadyAdded: Set<String>,
    onAdd: (LibraryExercise) -> Unit,
    onDismiss: () -> Unit
) {
    var search by rememberSaveable { mutableStateOf("") }
    val visible = exercises.filter { it.name.contains(search.trim(), ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add exercises") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Search library") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (visible.isEmpty()) {
                    Text("No matching active exercises. Create or restore one in Exercises.")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(visible, key = { it.id }) { item ->
                            val added = item.normalizedName in alreadyAdded
                            Card(
                                onClick = { if (!added) onAdd(item) },
                                enabled = !added,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(item.name, fontWeight = FontWeight.SemiBold)
                                        val weight = item.defaultWeightKg?.let { " @ ${it.clean()} kg" }.orEmpty()
                                        Text("${item.defaultSets} × ${item.defaultReps}$weight • ${item.defaultRestSeconds}s")
                                    }
                                    Text(if (added) "Added" else "Add", color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun ExerciseDraftEditor(
    number: Int,
    draft: ExerciseDraft,
    onChange: (ExerciseDraft) -> Unit,
    onRemove: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Exercise $number", fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onRemove) { Text("Remove") }
            }
            OutlinedTextField(
                value = draft.name,
                onValueChange = { onChange(draft.copy(name = it)) },
                label = { Text("Exercise name") },
                placeholder = { Text("Bench Press") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            DecimalField(
                "Target weight kg (optional)",
                draft.weightKg,
                { onChange(draft.copy(weightKg = it)) },
                Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IntegerField("Target sets", draft.sets, { onChange(draft.copy(sets = it)) }, Modifier.weight(1f))
                IntegerField("Target reps", draft.reps, { onChange(draft.copy(reps = it)) }, Modifier.weight(1f))
            }
            IntegerField(
                "Rest between sets (seconds)",
                draft.restSeconds,
                { onChange(draft.copy(restSeconds = it)) },
                Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ActiveWorkoutScreen(
    workout: TemplateWithExercises,
    alertMode: AlertMode,
    restoredDraft: ActiveWorkoutDraft?,
    onDraftChange: (ActiveWorkoutDraft) -> Unit,
    onCancel: () -> Unit,
    onFinish: (Long, List<CompletedExerciseInput>, Boolean) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val startedAt = remember(workout.template.id) {
        restoredDraft?.takeIf { it.templateId == workout.template.id }?.startedAt
            ?: System.currentTimeMillis()
    }
    var runs by remember(workout.template.id) {
        mutableStateOf(workout.orderedExercises.mapIndexed { index, exercise ->
            val restoredSets = restoredDraft
                ?.takeIf { it.templateId == workout.template.id }
                ?.setsByExercise
                ?.getOrNull(index)
            ExerciseRun(
                exercise = exercise,
                sets = restoredSets?.takeIf { it.size == exercise.targetSets }
                    ?: List(exercise.targetSets) {
                        SetRun(
                            weightKg = exercise.targetWeightKg?.clean().orEmpty(),
                            reps = exercise.targetReps.toString()
                        )
                    }
            )
        })
    }
    var timerRemaining by rememberSaveable(workout.template.id) { mutableIntStateOf(0) }
    var timerRunning by rememberSaveable(workout.template.id) { mutableStateOf(false) }
    var confirmPartialFinish by rememberSaveable(workout.template.id) { mutableStateOf(false) }
    var confirmCancel by rememberSaveable(workout.template.id) { mutableStateOf(false) }
    LaunchedEffect(runs, startedAt) {
        onDraftChange(
            ActiveWorkoutDraft(
                templateId = workout.template.id,
                startedAt = startedAt,
                setsByExercise = runs.map { it.sets }
            )
        )
    }
    LaunchedEffect(timerRunning, timerRemaining) {
        if (timerRunning && timerRemaining > 0) {
            delay(1_000)
            timerRemaining -= 1
        } else if (timerRunning) {
            playRestAlert(context, alertMode)
            timerRunning = false
        }
    }

    val completedCount = runs.sumOf { run -> run.sets.count { it.completedAt != null } }
    val totalCount = runs.sumOf { it.sets.size }
    val allComplete = totalCount > 0 && completedCount == totalCount
    val finishSession = {
        onFinish(startedAt, runs.mapIndexedNotNull { index, run ->
            val completedSets = run.sets.filter { it.completedAt != null }
            if (completedSets.isEmpty()) return@mapIndexedNotNull null
            CompletedExerciseInput(
                name = run.exercise.name,
                plannedWeightKg = run.exercise.targetWeightKg,
                targetSets = run.exercise.targetSets,
                targetReps = run.exercise.targetReps,
                restSeconds = run.exercise.restSeconds,
                position = index,
                sets = completedSets.map { set ->
                    CompletedSetInput(
                        weightKg = set.weightKg.toDoubleOrNull(),
                        reps = set.reps.toInt(),
                        completedAt = set.completedAt!!
                    )
                }
            )
        }, allComplete)
    }

    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(32.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(workout.template.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("$completedCount of $totalCount sets completed")
            }
            TextButton(onClick = { confirmCancel = true }) { Text("Cancel") }
        }

        Card(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Rest timer", style = MaterialTheme.typography.labelLarge)
                    Text(
                        if (timerRunning || timerRemaining > 0) formatTimer(timerRemaining) else "Ready",
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
                Row {
                    TextButton(
                        enabled = timerRunning || timerRemaining > 0,
                        onClick = { timerRemaining += 30 }
                    ) { Text("+30s") }
                    TextButton(
                        enabled = timerRunning || timerRemaining > 0,
                        onClick = { timerRunning = false; timerRemaining = 0 }
                    ) { Text("Skip") }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(runs) { exerciseIndex, run ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(run.exercise.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Target ${run.exercise.targetSets} × ${run.exercise.targetReps}" +
                                run.exercise.targetWeightKg?.let { " @ ${it.clean()} kg" }.orEmpty()
                        )
                        OutlinedButton(
                            onClick = {
                                focusManager.clearFocus(force = true)
                                keyboardController?.hide()
                                timerRemaining = run.exercise.restSeconds
                                timerRunning = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Start ${run.exercise.restSeconds}s rest timer") }

                        run.sets.forEachIndexed { setIndex, set ->
                            val valid = set.reps.toIntOrNull()?.let { it > 0 } == true &&
                                (set.weightKg.isBlank() || set.weightKg.toDoubleOrNull()?.let { it >= 0 } == true)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("${setIndex + 1}", modifier = Modifier.padding(top = 18.dp))
                                DecimalField(
                                    "kg",
                                    set.weightKg,
                                    { value ->
                                        runs = runs.updateSet(exerciseIndex, setIndex, set.copy(weightKg = value))
                                    },
                                    Modifier.weight(1f)
                                )
                                IntegerField(
                                    "reps",
                                    set.reps,
                                    { value ->
                                        runs = runs.updateSet(exerciseIndex, setIndex, set.copy(reps = value))
                                    },
                                    Modifier.weight(1f)
                                )
                                Button(
                                    enabled = valid && set.completedAt == null,
                                    onClick = {
                                        focusManager.clearFocus(force = true)
                                        keyboardController?.hide()
                                        runs = runs.updateSet(
                                            exerciseIndex,
                                            setIndex,
                                            set.copy(completedAt = System.currentTimeMillis())
                                        )
                                        timerRemaining = run.exercise.restSeconds
                                        timerRunning = true
                                    },
                                    modifier = Modifier.padding(top = 7.dp)
                                ) { Text(if (set.completedAt == null) "Done" else "✓") }
                            }
                        }
                    }
                }
            }
        }

        Button(
            enabled = completedCount > 0,
            onClick = {
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
                if (allComplete) finishSession() else confirmPartialFinish = true
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Finish workout") }
        Spacer(Modifier.height(18.dp))
    }

    if (confirmPartialFinish) {
        AlertDialog(
            onDismissRequest = { confirmPartialFinish = false },
            title = { Text("Finish partial workout?") },
            text = {
                Text("You completed $completedCount of $totalCount planned sets. The workout will be saved as Partial in History.")
            },
            confirmButton = {
                Button(onClick = {
                    confirmPartialFinish = false
                    finishSession()
                }) { Text("Finish and save") }
            },
            dismissButton = {
                TextButton(onClick = { confirmPartialFinish = false }) { Text("Keep working") }
            }
        )
    }
    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("Discard this workout?") },
            text = { Text("Completed sets and entered values will be removed. Closing or locking the phone does not discard the workout.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmCancel = false
                    onCancel()
                }) { Text("Discard workout") }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }) { Text("Keep workout") }
            }
        )
    }
}

private fun List<ExerciseRun>.updateSet(
    exerciseIndex: Int,
    setIndex: Int,
    value: SetRun
): List<ExerciseRun> = toMutableList().also { exercises ->
    val run = exercises[exerciseIndex]
    exercises[exerciseIndex] = run.copy(
        sets = run.sets.toMutableList().also { it[setIndex] = value }
    )
}

@Composable
private fun HistoryScreen(
    sessionHistory: List<SessionSummary>,
    legacyEntries: List<ExerciseEntry>,
    onExportCsv: () -> Unit,
    onExportJson: () -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formatter = remember { DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a") }
    var showExport by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Completed workouts", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showExport = true }) { Text("Export") }
                TextButton(
                    onClick = { confirmClear = true },
                    enabled = sessionHistory.isNotEmpty() || legacyEntries.isNotEmpty()
                ) { Text("Clear history") }
            }
        }
        if (sessionHistory.isEmpty()) {
            item { Text("Finished workouts will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(sessionHistory, key = { "session-${it.id}" }) { session ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(session.workoutName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (session.completedAsPlanned) "Completed" else "Partial",
                        color = if (session.completedAsPlanned) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text("${session.exerciseCount} exercises • ${session.setCount} sets")
                    Text(
                        Instant.ofEpochMilli(session.completedAt).atZone(ZoneId.systemDefault()).format(formatter),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (legacyEntries.isNotEmpty()) {
            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Earlier quick records", style = MaterialTheme.typography.titleMedium)
            }
            items(legacyEntries, key = { "legacy-${it.id}" }) { EntryCard(it) }
        }
    }
    if (showExport) {
        AlertDialog(
            onDismissRequest = { showExport = false },
            title = { Text("Export history") },
            text = { Text("CSV is convenient for spreadsheets. JSON preserves the full structured backup.") },
            confirmButton = {
                TextButton(onClick = { showExport = false; onExportCsv() }) { Text("Save CSV") }
            },
            dismissButton = {
                TextButton(onClick = { showExport = false; onExportJson() }) { Text("Save JSON") }
            }
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear workout history?") },
            text = { Text("This permanently deletes completed sessions and quick records. Your workout templates remain available.") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; onClearHistory() }) { Text("Delete history") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmClear = false; showExport = true }) { Text("Export first") }
                    TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
                }
            }
        )
    }
}

@Composable
private fun ProgressScreen(analytics: WorkoutAnalytics, modifier: Modifier = Modifier) {
    val volume = remember(analytics.monthlyVolumeKg) {
        if (analytics.monthlyVolumeKg >= 1000) "%.1f t".format(analytics.monthlyVolumeKg / 1000) else "%.0f kg".format(analytics.monthlyVolumeKg)
    }
    var selectedExercise by remember(analytics.exerciseProgress.keys) {
        mutableStateOf(analytics.exerciseProgress.keys.firstOrNull())
    }
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("This month", style = MaterialTheme.typography.titleLarge) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard("Volume", volume, Modifier.weight(1f))
                MetricCard("Workouts", analytics.monthlyWorkouts.toString(), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard("Sets", analytics.monthlySets.toString(), Modifier.weight(1f))
                MetricCard("Training days", analytics.monthlyTrainingDays.toString(), Modifier.weight(1f))
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Weekly volume", fontWeight = FontWeight.Bold)
                    VolumeBars(analytics.weeklyVolumeKg, Modifier.fillMaxWidth().height(130.dp).padding(top = 12.dp))
                }
            }
        }
        item { Text("Personal records", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        if (analytics.personalRecords.isEmpty()) item { Text("Complete weighted sets to establish records.") }
        items(analytics.personalRecords, key = { it.exerciseName }) { record ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(record.exerciseName, modifier = Modifier.weight(1f))
                    Text("${formatWeight(record.weightKg)} kg × ${record.reps}", fontWeight = FontWeight.Bold)
                }
            }
        }
        if (analytics.exerciseProgress.isNotEmpty()) {
            item {
                Text("Exercise progress", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(analytics.exerciseProgress.keys.toList()) { name ->
                        FilterChip(selectedExercise == name, { selectedExercise = name }, { Text(name) })
                    }
                }
                val points = selectedExercise?.let { analytics.exerciseProgress[it] }.orEmpty()
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Heaviest set per session")
                        ProgressLine(points.map { it.maxWeightKg }, Modifier.fillMaxWidth().height(130.dp).padding(top = 12.dp))
                    }
                }
            }
        }
        item {
            Text(
                "Volume is weight × reps for completed sets. Partial workouts count too.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) { Column(Modifier.padding(14.dp)) { Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(label) } }
}

@Composable
private fun VolumeBars(values: List<Double>, modifier: Modifier = Modifier) {
    val barColor = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val maximum = values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        val slot = size.width / values.size.coerceAtLeast(1)
        values.forEachIndexed { index, value ->
            val height = (size.height * (value / maximum)).toFloat()
            drawRect(barColor, Offset(index * slot + slot * .18f, size.height - height), androidx.compose.ui.geometry.Size(slot * .64f, height))
        }
    }
}

@Composable
private fun ProgressLine(values: List<Double>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val minimum = values.minOrNull() ?: 0.0
        val range = ((values.maxOrNull() ?: minimum) - minimum).coerceAtLeast(1.0)
        val points = values.mapIndexed { index, value ->
            Offset(
                if (values.size == 1) size.width / 2 else size.width * index / (values.size - 1),
                size.height - (size.height * ((value - minimum) / range)).toFloat()
            )
        }
        points.zipWithNext().forEach { (a, b) -> drawLine(lineColor, a, b, strokeWidth = 5f) }
        points.forEach { drawCircle(lineColor, 7f, it) }
    }
}

private fun formatWeight(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

@Composable
private fun AlertSettingsDialog(
    selected: AlertMode,
    onSelect: (AlertMode) -> Unit,
    onTest: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rest timer alert") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Choose how LocoGym alerts you when rest time ends.")
                AlertMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected == mode, onClick = { onSelect(mode) })
                        Text(
                            when (mode) {
                                AlertMode.SOUND -> "Sound"
                                AlertMode.VIBRATION -> "Vibration"
                                AlertMode.BOTH -> "Sound and vibration"
                            }
                        )
                    }
                }
                Text(
                    "Sound uses the phone's alarm volume.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = { TextButton(onClick = onTest) { Text("Test alert") } }
    )
}

private suspend fun playRestAlert(context: Context, mode: AlertMode) {
    if (mode == AlertMode.VIBRATION || mode == AlertMode.BOTH) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 400, 150, 400, 150, 600), -1)
        )
    }

    if (mode == AlertMode.SOUND || mode == AlertMode.BOTH) {
        val ringtone = runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: return@runCatching null
            RingtoneManager.getRingtone(context, uri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            }
        }.getOrNull()

        if (ringtone != null) {
            try {
                ringtone.play()
                delay(2_500)
            } finally {
                ringtone.stop()
            }
        } else {
            val tone = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            try {
                tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1_800)
                delay(1_900)
            } finally {
                tone.stopTone()
                tone.release()
            }
        }
    }
}

private fun formatTimer(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

@Composable
private fun WorkoutLog(
    entries: List<ExerciseEntry>,
    onSaveEntry: (String, Double, Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var exercise by rememberSaveable { mutableStateOf("") }
    var weight by rememberSaveable { mutableStateOf("") }
    var reps by rememberSaveable { mutableStateOf("") }
    var sets by rememberSaveable { mutableStateOf("") }
    val weightValue = weight.toDoubleOrNull()
    val repsValue = reps.toIntOrNull()
    val setsValue = sets.toIntOrNull()
    val valid = exercise.isNotBlank() && weightValue != null && weightValue >= 0.0 &&
        repsValue != null && repsValue > 0 && setsValue != null && setsValue > 0

    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Record a set", style = MaterialTheme.typography.titleLarge) }
        item {
            OutlinedTextField(
                value = exercise,
                onValueChange = { exercise = it },
                label = { Text("Exercise") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DecimalField("Weight (kg)", weight, { weight = it }, Modifier.weight(1.4f))
                IntegerField("Reps", reps, { reps = it }, Modifier.weight(1f))
                IntegerField("Sets", sets, { sets = it }, Modifier.weight(1f))
            }
        }
        item {
            Button(
                enabled = valid,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    onSaveEntry(exercise, weightValue!!, repsValue!!, setsValue!!)
                    exercise = ""; weight = ""; reps = ""; sets = ""
                }
            ) { Text("Save workout") }
        }
        item {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Saved workouts", style = MaterialTheme.typography.titleLarge)
            if (entries.isEmpty()) Text("Your first workout will appear here.")
        }
        items(entries, key = { it.id }) { EntryCard(it) }
    }
}

@Composable
private fun IntegerField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.all(Char::isDigit)) onChange(it) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier
    )
}

@Composable
private fun DecimalField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = {
            if (it.isEmpty() || (it.count { char -> char == '.' } <= 1 && it.all { char -> char.isDigit() || char == '.' })) onChange(it)
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = modifier
    )
}

@Composable
private fun EntryCard(entry: ExerciseEntry) {
    val formatter = remember { DateTimeFormatter.ofPattern("d MMM, h:mm a") }
    val date = Instant.ofEpochMilli(entry.createdAt).atZone(ZoneId.systemDefault()).format(formatter)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(entry.exercise, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("${entry.sets} sets × ${entry.reps} reps @ ${entry.weightKg.clean()} kg")
            Text(date, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun Double.clean(): String = if (this % 1.0 == 0.0) toInt().toString() else toString()
