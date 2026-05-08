package com.example.carjournal.data.repository

import com.example.carjournal.data.db.CarDao
import com.example.carjournal.data.model.Car
import com.example.carjournal.data.model.MaintenanceRecord
import kotlinx.coroutines.flow.Flow

/**
 * Репозиторий — единственный источник данных для ViewModel.
 * Инкапсулирует работу с DAO, изолируя ViewModel от деталей Room.
 */
class CarRepository(private val dao: CarDao) {

    // Автомобили
    fun getAllCars(): Flow<List<Car>> = dao.getAllCars()
    fun getCarById(carId: Int): Flow<Car?> = dao.getCarById(carId)
    suspend fun insertCar(car: Car) = dao.insertCar(car)
    suspend fun deleteCar(car: Car) = dao.deleteCar(car)

    // Записи ТО
    fun getRecordsForCar(carId: Int): Flow<List<MaintenanceRecord>> = dao.getRecordsForCar(carId)
    suspend fun insertRecord(record: MaintenanceRecord) = dao.insertRecord(record)
    suspend fun deleteRecord(record: MaintenanceRecord) = dao.deleteRecord(record)
}
