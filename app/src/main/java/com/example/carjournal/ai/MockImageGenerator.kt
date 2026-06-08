package com.example.carjournal.ai

import kotlinx.coroutines.delay

/**
 * ЗАГЛУШКА генератора изображений.
 * Имитирует сетевую задержку перед возвратом результата.
 */
class MockImageGenerator : ImageGenerationStrategy {
    override suspend fun generateImage(color: String, brand: String, model: String, year: Int, skipUrls: Set<String>): String? {
        delay(2000L) // Имитируем задержку обработки
        return null  // null → ViewModel покажет локальный drawable-placeholder
    }
}
