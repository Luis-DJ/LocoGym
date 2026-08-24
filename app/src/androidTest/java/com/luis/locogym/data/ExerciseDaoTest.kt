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
class ExerciseDaoTest {
    private lateinit var database: LocoGymDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LocoGymDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After fun tearDown() = database.close()

    @Test
    fun insertedExerciseCanBeReadBack() = runBlocking {
        database.exerciseDao().insert(ExerciseEntry(exercise = "Squat", weightKg = 80.0, reps = 5, sets = 3))
        val saved = database.exerciseDao().observeAll().first()
        assertEquals(1, saved.size)
        assertEquals("Squat", saved.single().exercise)
        assertEquals(80.0, saved.single().weightKg, 0.0)
        assertEquals(5, saved.single().reps)
        assertEquals(3, saved.single().sets)
    }
}
