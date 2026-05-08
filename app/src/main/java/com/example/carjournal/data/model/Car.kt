package com.example.carjournal.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Сущность "Автомобиль" — хранится в таблице Room.
 * В будущем (диплом) сюда можно добавить поле generatedImagePath
 * для сохранения пути к AI-сгенерированному изображению.
 */
@Entity(tableName = "cars")
data class Car(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val brand: String,       // Марка
    val model: String,       // Модель
    val year: Int,           // Год выпуска
    val color: String        // Цвет (используется MockImageGenerator для выбора заглушки)
)
