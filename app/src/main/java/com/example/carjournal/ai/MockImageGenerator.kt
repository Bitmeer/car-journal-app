package com.example.carjournal.ai

import kotlinx.coroutines.delay

/**
 * ЗАГЛУШКА генератора изображений.
 * Имитирует задержку сети 2 секунды, затем возвращает null,
 * что заставляет ViewModel отобразить локальный drawable-placeholder.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * TODO (Диплом): Заменить тело generateImage() на реальную реализацию:
 *
 *   Вариант А — TFLite (локально, без интернета):
 *     val interpreter = Interpreter(loadModelFile(context))
 *     val latentVector = encodePrompt("$color $brand $model")
 *     val outputBitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
 *     interpreter.run(latentVector, outputBitmap)
 *     return saveBitmapToCache(context, outputBitmap)
 *
 *   Вариант Б — Stable Diffusion / DALL·E REST API:
 *     val prompt = "a $color $brand $model car, photorealistic, studio lighting"
 *     val response = stableDiffusionApiService.generateImage(GenerateRequest(prompt))
 *     return response.imageUrl
 * ─────────────────────────────────────────────────────────────────────────────
 */
class MockImageGenerator : ImageGenerationStrategy {
    override suspend fun generateImage(color: String, brand: String, model: String, year: Int, skipUrls: Set<String>): String? {
        delay(2000L) // Имитируем задержку обработки
        return null  // null → ViewModel покажет локальный drawable-placeholder
    }
}
