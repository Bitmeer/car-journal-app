package com.example.carjournal.ai

import com.example.carjournal.network.WikipediaImageApi

/**
 * Поиск изображения автомобиля через Wikipedia/Wikimedia API.
 */
class NetworkCarImageGenerator : ImageGenerationStrategy {

    override suspend fun generateImage(color: String, brand: String, model: String, year: Int, skipUrls: Set<String>): String? {
        return WikipediaImageApi.getCarImageUrl(
            brand = brand,
            model = model,
            color = color,
            year = year,
            skipUrls = skipUrls
        )
    }
}
