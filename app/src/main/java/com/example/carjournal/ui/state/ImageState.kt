package com.example.carjournal.ui.state

import android.graphics.Bitmap

/**
 * Состояние процесса получения изображения автомобиля из сети.
 */
sealed class ImageState {
    /** Изображение ещё не запрашивалось */
    object Idle : ImageState()

    /** Идёт генерация / поиск */
    object Loading : ImageState()

    /** Изображение сгенерировано AI (Gemini) — готовый Bitmap */
    data class GeneratedBitmap(val bitmap: Bitmap) : ImageState()

    /** Найдено изображение — url для загрузки через Coil */
    data class NetworkUrl(val url: String) : ImageState()

    /** Изображение не найдено — показываем локальную заглушку */
    object LocalFallback : ImageState()

    /** Ошибка при запросе (нет сети, таймаут, и т.д.) */
    data class Error(val message: String) : ImageState()
}
