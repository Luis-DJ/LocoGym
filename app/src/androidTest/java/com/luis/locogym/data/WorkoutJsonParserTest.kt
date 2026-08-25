package com.luis.locogym.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutJsonParserTest {
    @Test
    fun parsesWeightAndRestFromVersionOneFile() {
        val parsed = WorkoutJsonParser.parse(
            """{
                "formatVersion": 1,
                "workoutTemplates": [{
                    "name": "Upper Body",
                    "description": "Test",
                    "exercises": [
                        {"name":"Bench Press","targetWeightKg":17.5,"targetSets":3,"targetReps":12,"restSeconds":60},
                        {"name":"Pull-up","targetWeightKg":null,"targetSets":3,"targetReps":8,"restSeconds":90}
                    ]
                }]
            }""".trimIndent()
        )

        val exercises = parsed.single().second
        assertEquals(17.5, exercises[0].targetWeightKg!!, 0.0)
        assertEquals(60, exercises[0].restSeconds)
        assertNull(exercises[1].targetWeightKg)
        assertEquals(90, exercises[1].restSeconds)
    }
}
