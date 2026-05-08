package com.example.carjournal.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Сущность "Запись технического обслуживания".
 * Привязана к конкретному автомобилю через внешний ключ carId.
 */
@Entity(
    tableName = "maintenance_records",
    foreignKeys = [
        ForeignKey(
            entity = Car::class,
            parentColumns = ["id"],
            childColumns = ["carId"],
            onDelete = ForeignKey.CASCADE   // При удалении авто удаляются все его записи
        )
    ],
    indices = [Index("carId")]
)
data class MaintenanceRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val carId: Int,              // Ссылка на Car.id
    val date: String,            // Дата обслуживания (формат: "ДД.ММ.ГГГГ")
    val mileage: Int,            // Пробег на момент обслуживания (км)
    val cost: Double,            // Стоимость (руб.)
    val workType: String         // Тип работ: "Замена масла", "ТО", и т.д.
)
