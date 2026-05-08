package com.example.carjournal.data.model

/**
 * Заправочная станция, полученная из OpenStreetMap (Overpass API).
 */
data class GasStation(
    val id: Long,
    val name: String,       // Название ("Лукойл", "Газпром" и т.д.)
    val brand: String,      // Бренд сети
    val lat: Double,
    val lon: Double,
    val fuelTypes: String = "" // Виды топлива (amenity=fuel tags)
)
