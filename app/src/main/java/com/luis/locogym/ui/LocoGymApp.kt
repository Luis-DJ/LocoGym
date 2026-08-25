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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luis.locogym.LocoGymViewModel
import com.luis.locogym.data.ExerciseEntry
import com.luis.locogym.data.CompletedExerciseInput
import com.luis.locogym.data.CompletedSetInput
import com.luis.locogym.data.SessionSummary
import com.luis.locogym.data.TemplateExercise
import com.luis.locogym.data.TemplateWithExercises
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class HomeSection { TEMPLATES, HISTORY }
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

@Composable
fun LocoGymApp(viewModel: LocoGymViewModel) {
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val sessionHistory by viewModel.sessionHistory.collectAsStateWithLifecycle()
    val importMessage by viewModel.importMessage.collectAsStateWithLifecycle()
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
    var section by rememberSaveable { mutableStateOf(HomeSection.TEMPLATES) }
    var editing by remember { mutableStateOf<TemplateWithExercises?>(null) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var activeWorkout by remember { mutableStateOf<TemplateWithExercises?>(null) }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (activeWorkout != null) {
                ActiveWorkoutScreen(
                    workout = activeWorkout!!,
                    alertMode = alertMode,
                    onCancel = { activeWorkout = null },
                    onFinish = { startedAt, completed ->
                        viewModel.finishSession(
                            templateId = activeWorkout!!.template.id,
                            workoutName = activeWorkout!!.template.name,
                            startedAt = startedAt,
                            exercises = completed
                        )
                        activeWorkout = null
                        section = HomeSection.HISTORY
                    }
                )
            } else if (editorOpen) {
                TemplateEditor(
                    existing = editing,
                    onCancel = { editorOpen = false },
                    onSave = { name, description, exercises ->
                        viewModel.saveTemplate(editing?.template, name, description, exercises)
                        editorOpen = false
                    }
                )
            } else {
                HomeScreen(
                    section = section,
                    onSectionChange = { section = it },
                    templates = templates,
                    entries = entries,
                    sessionHistory = sessionHistory,
                    onNewTemplate = { editing = null; editorOpen = true },
                    onEditTemplate = { editing = it; editorOpen = true },
                    onStartWorkout = { activeWorkout = it },
                    onImportTemplates = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                    importMessage = importMessage,
                    onDismissImportMessage = viewModel::clearImportMessage,
                    onAlertSettings = { showAlertSettings = true }
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
    entries: List<ExerciseEntry>,
    sessionHistory: List<SessionSummary>,
    onNewTemplate: () -> Unit,
    onEditTemplate: (TemplateWithExercises) -> Unit,
    onStartWorkout: (TemplateWithExercises) -> Unit,
    onImportTemplates: () -> Unit,
    importMessage: String?,
    onDismissImportMessage: () -> Unit,
    onAlertSettings: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = section == HomeSection.TEMPLATES,
                onClick = { onSectionChange(HomeSection.TEMPLATES) },
                label = { Text("My Workouts") }
            )
            FilterChip(
                selected = section == HomeSection.HISTORY,
                onClick = { onSectionChange(HomeSection.HISTORY) },
                label = { Text("History") }
            )
        }
        Spacer(Modifier.height(12.dp))
        when (section) {
            HomeSection.TEMPLATES -> TemplateList(
                templates = templates,
                onNewTemplate = onNewTemplate,
                onEditTemplate = onEditTemplate,
                onStartWorkout = onStartWorkout,
                onImportTemplates = onImportTemplates,
                importMessage = importMessage,
                onDismissImportMessage = onDismissImportMessage,
                modifier = Modifier.weight(1f)
            )
            HomeSection.HISTORY -> HistoryScreen(
                sessionHistory = sessionHistory,
                legacyEntries = entries,
                modifier = Modifier.weight(1f)
            )
        }
        Text("v0.4.1-dev • stored only on this device", style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun TemplateList(
    templates: List<TemplateWithExercises>,
    onNewTemplate: () -> Unit,
    onEditTemplate: (TemplateWithExercises) -> Unit,
    onStartWorkout: (TemplateWithExercises) -> Unit,
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
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.template.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (item.template.description.isNotBlank()) {
                        Text(item.template.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        "${item.exercises.size} ${if (item.exercises.size == 1) "exercise" else "exercises"}",
                        style = MaterialTheme.typography.labelLarge
                    )
                    item.orderedExercises.take(3).forEach { exercise ->
                        val weight = exercise.targetWeightKg?.let { " @ ${it.clean()} kg" }.orEmpty()
                        Text("${exercise.name} • ${exercise.targetSets} × ${exercise.targetReps}$weight • ${exercise.restSeconds}s rest")
                    }
                    if (item.exercises.size > 3) Text("+ ${item.exercises.size - 3} more")
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onStartWorkout(item) }, modifier = Modifier.weight(1f)) {
                            Text("Start workout")
                        }
                        OutlinedButton(onClick = { onEditTemplate(item) }) { Text("Edit") }
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateEditor(
    existing: TemplateWithExercises?,
    onCancel: () -> Unit,
    onSave: (String, String, List<TemplateExercise>) -> Unit
) {
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

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
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
                OutlinedButton(
                    onClick = { exercises = exercises + ExerciseDraft() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Add exercise") }
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
    onCancel: () -> Unit,
    onFinish: (Long, List<CompletedExerciseInput>) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val startedAt = remember(workout.template.id) { System.currentTimeMillis() }
    var runs by remember(workout.template.id) {
        mutableStateOf(workout.orderedExercises.map { exercise ->
            ExerciseRun(
                exercise = exercise,
                sets = List(exercise.targetSets) {
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
    LaunchedEffect(timerRunning, timerRemaining) {
        if (timerRunning && timerRemaining > 0) {
            delay(1_000)
            timerRemaining -= 1
        } else if (timerRunning) {
            timerRunning = false
            playRestAlert(context, alertMode)
        }
    }

    val completedCount = runs.sumOf { run -> run.sets.count { it.completedAt != null } }
    val totalCount = runs.sumOf { it.sets.size }
    val allComplete = totalCount > 0 && completedCount == totalCount

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
            TextButton(onClick = onCancel) { Text("Cancel") }
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
            enabled = allComplete,
            onClick = {
                onFinish(startedAt, runs.mapIndexed { index, run ->
                    CompletedExerciseInput(
                        name = run.exercise.name,
                        plannedWeightKg = run.exercise.targetWeightKg,
                        targetSets = run.exercise.targetSets,
                        targetReps = run.exercise.targetReps,
                        restSeconds = run.exercise.restSeconds,
                        position = index,
                        sets = run.sets.map { set ->
                            CompletedSetInput(
                                weightKg = set.weightKg.toDoubleOrNull(),
                                reps = set.reps.toInt(),
                                completedAt = set.completedAt!!
                            )
                        }
                    )
                })
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Finish workout") }
        Spacer(Modifier.height(18.dp))
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
    modifier: Modifier = Modifier
) {
    val formatter = remember { DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a") }
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Completed workouts", style = MaterialTheme.typography.titleLarge) }
        if (sessionHistory.isEmpty()) {
            item { Text("Finished workouts will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(sessionHistory, key = { "session-${it.id}" }) { session ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(session.workoutName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
}

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
        val played = runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: return@runCatching false
            val ringtone = RingtoneManager.getRingtone(context, uri)
                ?: return@runCatching false
            ringtone.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            ringtone.play()
            delay(2_500)
            ringtone.stop()
            true
        }.getOrDefault(false)

        if (!played) {
            val tone = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1_800)
            delay(1_900)
            tone.release()
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
