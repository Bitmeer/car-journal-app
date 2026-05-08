package com.example.carjournal.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.carjournal.data.model.Car
import com.example.carjournal.data.model.MaintenanceRecord

/**
 * Главный класс базы данных Room.
 * Singleton — создаётся один раз для всего приложения.
 */
@Database(
    entities = [Car::class, MaintenanceRecord::class],
    version = 1,
    exportSchema = false
)
abstract class CarDatabase : RoomDatabase() {

    abstract fun carDao(): CarDao

    companion object {
        @Volatile
        private var INSTANCE: CarDatabase? = null

        fun getDatabase(context: Context): CarDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CarDatabase::class.java,
                    "car_journal_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
