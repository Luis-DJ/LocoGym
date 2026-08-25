package com.luis.locogym.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luis.locogym.LocoGymViewModel
import com.luis.locogym.data.ExerciseEntry
import com.luis.locogym.data.TemplateExercise
import com.luis.locogym.data.TemplateWithExercises
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class HomeSection { TEMPLATES, LOG }

private data class ExerciseDraft(
    val name: String = "",
    val sets: String = "3",
    val reps: String = "8"
)

@Composable
fun LocoGymApp(viewModel: LocoGymViewModel) {
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    var section by rememberSaveable { mutableStateOf(HomeSection.TEMPLATES) }
    var editing by remember { mutableStateOf<TemplateWithExercises?>(null) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (editorOpen) {
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
                    onNewTemplate = { editing = null; editorOpen = true },
                    onEditTemplate = { editing = it; editorOpen = true },
                    onSaveEntry = viewModel::save
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    section: HomeSection,
    onSectionChange: (HomeSection) -> Unit,
    templates: List<TemplateWithExercises>,
    entries: List<ExerciseEntry>,
    onNewTemplate: () -> Unit,
    onEditTemplate: (TemplateWithExercises) -> Unit,
    onSaveEntry: (String, Double, Int, Int) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(36.dp))
        Text("LocoGym", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("No account. No cloud. No nonsense.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = section == HomeSection.TEMPLATES,
                onClick = { onSectionChange(HomeSection.TEMPLATES) },
                label = { Text("Templates") }
            )
            FilterChip(
                selected = section == HomeSection.LOG,
                onClick = { onSectionChange(HomeSection.LOG) },
                label = { Text("Workout log") }
            )
        }
        Spacer(Modifier.height(12.dp))
        when (section) {
            HomeSection.TEMPLATES -> TemplateList(
                templates, onNewTemplate, onEditTemplate, Modifier.weight(1f)
            )
            HomeSection.LOG -> WorkoutLog(entries, onSaveEntry, Modifier.weight(1f))
        }
        Text("v0.2.0-dev • stored only on this device", style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun TemplateList(
    templates: List<TemplateWithExercises>,
    onNewTemplate: () -> Unit,
    onEditTemplate: (TemplateWithExercises) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Button(onClick = onNewTemplate, modifier = Modifier.fillMaxWidth()) {
                Text("Create template")
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
            Card(onClick = { onEditTemplate(item) }, modifier = Modifier.fillMaxWidth()) {
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
                        Text("${exercise.name} • ${exercise.targetSets} × ${exercise.targetReps}")
                    }
                    if (item.exercises.size > 3) Text("+ ${item.exercises.size - 3} more")
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
            ExerciseDraft(it.name, it.targetSets.toString(), it.targetReps.toString())
        }.orEmpty())
    }
    val valid = name.isNotBlank() && exercises.isNotEmpty() && exercises.all {
        it.name.isNotBlank() && (it.sets.toIntOrNull() ?: 0) > 0 && (it.reps.toIntOrNull() ?: 0) > 0
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(36.dp))
        Text(
            if (existing == null) "New template" else "Edit template",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Template name") },
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
                            targetSets = draft.sets.toInt(),
                            targetReps = draft.reps.toInt(),
                            position = index
                        )
                    })
                },
                modifier = Modifier.weight(1f)
            ) { Text("Save template") }
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IntegerField("Target sets", draft.sets, { onChange(draft.copy(sets = it)) }, Modifier.weight(1f))
                IntegerField("Target reps", draft.reps, { onChange(draft.copy(reps = it)) }, Modifier.weight(1f))
            }
        }
    }
}

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
