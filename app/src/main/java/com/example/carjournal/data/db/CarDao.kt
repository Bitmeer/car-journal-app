package com.example.carjournal.data.db

import androidx.room.*
import com.example.carjournal.data.model.Car
import com.example.carjournal.data.model.MaintenanceRecord
import kotlinx.coroutines.flow.Flow

/**
 * DAO (Data Access Object) — единая точка доступа к таблицам Room.
 * Все запросы возвращают Flow<> для реактивного обновления UI.
 */
@Dao
interface CarDao {

    // ─────────────────────────────
    //  Операции с автомобилями
    // ─────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCar(car: Car)

    @Delete
    suspend fun deleteCar(car: Car)

    @Query("SELECT * FROM cars ORDER BY id DESC")
    fun getAllCars(): Flow<List<Car>>

    @Query("SELECT * FROM cars WHERE id = :carId")
    fun getCarById(carId: Int): Flow<Car?>

    // ─────────────────────────────
    //  Операции с записями ТО
    // ─────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: MaintenanceRecord)

    @Delete
    suspend fun deleteRecord(record: MaintenanceRecord)

    @Query("SELECT * FROM maintenance_records WHERE carId = :carId ORDER BY id DESC")
    fun getRecordsForCar(carId: Int): Flow<List<MaintenanceRecord>>
}
