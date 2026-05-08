package com.example.carjournal.network

import com.example.carjournal.data.model.GasStation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Клиент к Overpass API (OpenStreetMap).
 * Бесплатно, без API-ключа — официальный публичный API OSM.
 *
 * Запрашивает все узлы с тегом amenity=fuel в радиусе [radiusMeters]
 * вокруг координат [lat], [lon].
 *
 * TODO (Диплом): Добавить фильтрацию по типу топлива (diesel, 95, 98).
 *               Добавить кэширование результатов в Room для офлайн-режима.
 */
object OverpassApi {

    private const val ENDPOINT = "https://overpass-api.de/api/interpreter"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Загружает ближайшие АЗС из OpenStreetMap.
     *
     * @param lat           Широта текущего местоположения
     * @param lon           Долгота текущего местоположения
     * @param radiusMeters  Радиус поиска (по умолчанию 5 км)
     */
    suspend fun getGasStations(
        lat: Double,
        lon: Double,
        radiusMeters: Int = 5000
    ): List<GasStation> = withContext(Dispatchers.IO) {
        try {
            // QL-запрос Overpass API: найти узлы с тегом amenity=fuel в радиусе
            val query = "[out:json][timeout:25];" +
                    "node[amenity=fuel](around:$radiusMeters,$lat,$lon);" +
                    "out body;"

            val body = "data=${URLEncoder.encode(query, "UTF-8")}"
                .toRequestBody("application/x-www-form-urlencoded".toMediaType())

            val request = Request.Builder()
                .url(ENDPOINT)
                .post(body)
                .header("User-Agent", "CarJournalApp/1.0 (coursework)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                parseStations(response.body?.string() ?: return@withContext emptyList())
            }
        } catch (e: Exception) {
            emptyList() // Нет сети — возвращаем пустой список
        }
    }

    private fun parseStations(json: String): List<GasStation> {
        return try {
            val elements = JSONObject(json).getJSONArray("elements")
            buildList {
                for (i in 0 until elements.length()) {
                    val el = elements.getJSONObject(i)
                    val lat = el.optDouble("lat", Double.NaN)
                    val lon = el.optDouble("lon", Double.NaN)
                    if (lat.isNaN() || lon.isNaN()) continue

                    val tags = el.optJSONObject("tags")
                    val brand = tags?.optString("brand", "") ?: ""
                    val name = tags?.optString("name", "")
                        ?.ifBlank { brand }
                        ?.ifBlank { "АЗС" } ?: "АЗС"
                    val fuel = buildString {
                        if (tags?.optString("fuel:diesel") == "yes") append("ДТ ")
                        if (tags?.optString("fuel:octane_95") == "yes") append("АИ-95 ")
                        if (tags?.optString("fuel:octane_98") == "yes") append("АИ-98 ")
                    }.trim()

                    add(GasStation(
                        id = el.optLong("id", 0L),
                        name = name,
                        brand = brand,
                        lat = lat,
                        lon = lon,
                        fuelTypes = fuel
                    ))
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
