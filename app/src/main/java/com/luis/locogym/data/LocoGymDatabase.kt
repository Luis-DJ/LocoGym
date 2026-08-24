package com.luis.locogym.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ExerciseEntry::class], version = 1, exportSchema = false)
abstract class LocoGymDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao

    companion object {
        @Volatile private var instance: LocoGymDatabase? = null

        fun getInstance(context: Context): LocoGymDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LocoGymDatabase::class.java,
                    "locogym.db"
                ).build().also { instance = it }
            }
    }
}
