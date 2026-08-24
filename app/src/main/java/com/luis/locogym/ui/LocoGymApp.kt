package com.luis.locogym.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luis.locogym.LocoGymViewModel
import com.luis.locogym.data.ExerciseEntry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun LocoGymApp(viewModel: LocoGymViewModel) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    var exercise by rememberSaveable { mutableStateOf("") }
    var weight by rememberSaveable { mutableStateOf("") }
    var reps by rememberSaveable { mutableStateOf("") }
    var sets by rememberSaveable { mutableStateOf("") }

    val weightValue = weight.toDoubleOrNull()
    val repsValue = reps.toIntOrNull()
    val setsValue = sets.toIntOrNull()
    val isValid = exercise.isNotBlank() && weightValue != null && weightValue >= 0.0 &&
        repsValue != null && repsValue > 0 && setsValue != null && setsValue > 0

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Spacer(Modifier.height(36.dp))
                    Text("LocoGym", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("No account. No cloud. No nonsense.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(20.dp))
                    Text("Record a set", style = MaterialTheme.typography.titleLarge)
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = exercise,
                            onValueChange = { exercise = it },
                            label = { Text("Exercise") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumberField("Weight (kg)", weight, { weight = it }, true, Modifier.weight(1.4f))
                            NumberField("Reps", reps, { reps = it }, false, Modifier.weight(1f))
                            NumberField("Sets", sets, { sets = it }, false, Modifier.weight(1f))
                        }
                        Button(
                            enabled = isValid,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                viewModel.save(exercise, weightValue!!, repsValue!!, setsValue!!)
                                exercise = ""; weight = ""; reps = ""; sets = ""
                            }
                        ) { Text("Save workout") }
                    }
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("Saved workouts", style = MaterialTheme.typography.titleLarge)
                    if (entries.isEmpty()) {
                        Text("Your first workout will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(entries, key = { it.id }) { EntryCard(it) }
                item {
                    Spacer(Modifier.height(20.dp))
                    Text("v0.1.0-dev • stored only on this device", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    decimal: Boolean,
    modifier: Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { next ->
            if (next.isEmpty() || next.all { it.isDigit() || (decimal && it == '.') }) onValueChange(next)
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        singleLine = true,
        modifier = modifier
    )
}

@Composable
private fun EntryCard(entry: ExerciseEntry) {
    val formatter = remember { DateTimeFormatter.ofPattern("d MMM, h:mm a") }
    val date = Instant.ofEpochMilli(entry.createdAt).atZone(ZoneId.systemDefault()).format(formatter)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(entry.exercise, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("${entry.sets} sets × ${entry.reps} reps @ ${entry.weightKg.clean()} kg")
            Text(date, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun Double.clean(): String = if (this % 1.0 == 0.0) toInt().toString() else toString()
