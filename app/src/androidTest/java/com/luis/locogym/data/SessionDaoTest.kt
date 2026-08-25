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
class SessionDaoTest {
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
    fun completedSessionAppearsInHistoryWithSnapshotCounts() = runBlocking {
        database.sessionDao().saveCompletedSession(
            templateId = 7,
            workoutName = "Upper Body",
            startedAt = 1000,
            completedAt = 2000,
            exercises = listOf(
                CompletedExerciseInput(
                    name = "Bench Press",
                    plannedWeightKg = 60.0,
                    targetSets = 2,
                    targetReps = 8,
                    restSeconds = 90,
                    position = 0,
                    sets = listOf(
                        CompletedSetInput(60.0, 8, 1200),
                        CompletedSetInput(60.0, 7, 1500)
                    )
                )
            )
        )

        val summary = database.sessionDao().observeHistory().first().single()
        assertEquals("Upper Body", summary.workoutName)
        assertEquals(1, summary.exerciseCount)
        assertEquals(2, summary.setCount)
    }
}
