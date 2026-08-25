package com.luis.locogym.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ExerciseEntry::class,
        WorkoutTemplate::class,
        TemplateExercise::class,
        WorkoutSession::class,
        SessionExercise::class,
        SessionSet::class
    ],
    version = 5,
    exportSchema = false
)
abstract class LocoGymDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun templateDao(): TemplateDao
    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile private var instance: LocoGymDatabase? = null

        fun getInstance(context: Context): LocoGymDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LocoGymDatabase::class.java,
                    "locogym.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { instance = it }
            }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `workout_templates` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )""".trimIndent()
                )
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `template_exercises` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `templateId` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `targetSets` INTEGER NOT NULL,
                        `targetReps` INTEGER NOT NULL,
                        `position` INTEGER NOT NULL,
                        FOREIGN KEY(`templateId`) REFERENCES `workout_templates`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )""".trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_template_exercises_templateId` " +
                        "ON `template_exercises` (`templateId`)"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `template_exercises` ADD COLUMN `targetWeightKg` REAL")
                database.execSQL(
                    "ALTER TABLE `template_exercises` ADD COLUMN `restSeconds` INTEGER NOT NULL DEFAULT 60"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `workout_sessions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `templateId` INTEGER,
                        `workoutName` TEXT NOT NULL,
                        `startedAt` INTEGER NOT NULL,
                        `completedAt` INTEGER NOT NULL
                    )""".trimIndent()
                )
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `session_exercises` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sessionId` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `plannedWeightKg` REAL,
                        `targetSets` INTEGER NOT NULL,
                        `targetReps` INTEGER NOT NULL,
                        `restSeconds` INTEGER NOT NULL,
                        `position` INTEGER NOT NULL,
                        FOREIGN KEY(`sessionId`) REFERENCES `workout_sessions`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )""".trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_session_exercises_sessionId` " +
                        "ON `session_exercises` (`sessionId`)"
                )
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `session_sets` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sessionExerciseId` INTEGER NOT NULL,
                        `position` INTEGER NOT NULL,
                        `weightKg` REAL,
                        `reps` INTEGER NOT NULL,
                        `completedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`sessionExerciseId`) REFERENCES `session_exercises`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )""".trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_session_sets_sessionExerciseId` " +
                        "ON `session_sets` (`sessionExerciseId`)"
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE `workout_sessions` ADD COLUMN " +
                        "`completedAsPlanned` INTEGER NOT NULL DEFAULT 1"
                )
            }
        }
    }
}
