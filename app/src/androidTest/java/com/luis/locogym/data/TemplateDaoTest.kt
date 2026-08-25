package com.luis.locogym.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TemplateDaoTest {
    private lateinit var database: LocoGymDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LocoGymDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun templateAndOrderedExercisesCanBeSavedAndEdited() = runBlocking {
        val dao = database.templateDao()
        dao.saveTemplate(
            WorkoutTemplate(name = "Upper Body", description = "Chest and back"),
            listOf(
                TemplateExercise(templateId = 0, name = "Bench Press", targetWeightKg = 60.0, targetSets = 3, targetReps = 8, restSeconds = 90, position = 0),
                TemplateExercise(templateId = 0, name = "Row", targetWeightKg = 50.0, targetSets = 3, targetReps = 10, restSeconds = 60, position = 1)
            )
        )

        val saved = dao.observeAll().first().single()
        assertEquals("Upper Body", saved.template.name)
        assertEquals(listOf("Bench Press", "Row"), saved.orderedExercises.map { it.name })

        dao.saveTemplate(
            saved.template.copy(description = "Updated"),
            listOf(
                TemplateExercise(templateId = saved.template.id, name = "Pull-up", targetWeightKg = null, targetSets = 4, targetReps = 6, restSeconds = 90, position = 0)
            )
        )

        val edited = dao.observeAll().first().single()
        assertEquals("Updated", edited.template.description)
        assertEquals(listOf("Pull-up"), edited.orderedExercises.map { it.name })
    }
}
