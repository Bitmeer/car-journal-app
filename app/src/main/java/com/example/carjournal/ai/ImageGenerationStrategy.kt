package com.example.carjournal.ai

/**
 * Стратегия генерации/поиска изображения автомобиля.
 *
 * Паттерн "Стратегия" позволяет менять реализацию без изменения ViewModel:
 *  - MockImageGenerator           — заглушка для разработки
 *  - NetworkCarImageGenerator     — реальный поиск через Wikipedia API (текущий)
 *  - TFLiteImageGenerator         — TODO(Диплом): генерация через TFLite локально
 *  - StableDiffusionApiGenerator  — TODO(Диплом): генерация через REST API
 */
interface ImageGenerationStrategy {

    /**
     * Ищет или генерирует изображение автомобиля по его параметрам.
     *
     * @param color  Цвет автомобиля
     * @param brand  Марка автомобиля (используется как поисковый запрос)
     * @param model  Модель автомобиля
     * @param year   Год выпуска (используется для точного поиска поколения модели)
     * @return URL изображения в виде строки, или null если не найдено/ошибка
     */
    suspend fun generateImage(color: String, brand: String, model: String, year: Int, skipUrls: Set<String> = emptySet()): String?
}
