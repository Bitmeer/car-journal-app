package com.example.carjournal.ai

import com.example.carjournal.network.WikipediaImageApi

/**
 * Реальный поиск изображения автомобиля через Wikipedia/Wikimedia API.
 *
 * Пользователь вводит марку и модель → приложение ищет изображение
 * в Wikipedia и показывает найденную фотографию.
 * Никаких API-ключей не требуется.
 *
 * TODO (Диплом): Заменить WikipediaImageApi на Stable Diffusion / DALL-E
 *               для полноценной AI-генерации вместо поиска готового фото.
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
