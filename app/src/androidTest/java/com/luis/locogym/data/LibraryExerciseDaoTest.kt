package com.luis.locogym.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryExerciseDaoTest {
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
    fun missingExercisesAreAddedAndCanBeArchivedAndRestored() = runBlocking {
        val dao = database.libraryExerciseDao()
        dao.addMissing(
            listOf(
                TemplateExercise(
                    templateId = 1,
                    name = "Cable Row",
                    targetWeightKg = 40.0,
                    targetSets = 3,
                    targetReps = 12,
                    restSeconds = 60,
                    position = 0
                ),
                TemplateExercise(
                    templateId = 2,
                    name = "cable row",
                    targetWeightKg = 45.0,
                    targetSets = 4,
                    targetReps = 10,
                    restSeconds = 90,
                    position = 0
                )
            )
        )

        val saved = dao.observeAll().first().single()
        assertEquals("Cable Row", saved.name)
        assertEquals(40.0, saved.defaultWeightKg!!, 0.0)
        assertFalse(saved.archived)

        dao.setArchived(saved.id, true)
        assertTrue(dao.observeAll().first().single().archived)
        dao.setArchived(saved.id, false)
        assertFalse(dao.observeAll().first().single().archived)
    }
}
