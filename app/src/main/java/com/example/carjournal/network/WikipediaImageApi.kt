package com.example.carjournal.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Поиск фотографии автомобиля через Bing, Яндекс и Google Картинки.
 * Никаких Wikipedia/Wikimedia — только браузерный поиск картинок.
 */
object WikipediaImageApi {

    // Google Custom Search JSON API
    private const val GOOGLE_API_KEY = "AIzaSyA51dptPNCVcmGlNu84_aIA6DbMtA97T8c"
    // Programmable Search Engine ID (ищет по всему вебу)
    // Создать своё: https://programmablesearchengine.google.com/
    private const val GOOGLE_CX = "017576662512468239146:omuauf_lfve"

    private const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", DESKTOP_UA)
                    .header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Encoding", "identity")
                    .header("Cache-Control", "no-cache")
                    .build()
            )
        }
        .build()

    private val LOGO_KEYWORDS = listOf(
        "logo", "badge", "emblem", "symbol", "crest", "marque",
        "icon", "ring", "wordmark", "seal", "insignia", "shield", "favicon"
    )

    suspend fun getCarImageUrl(
        brand: String,
        model: String,
        color: String = "",
        year: Int = 0,
        skipUrls: Set<String> = emptySet()
    ): String? = withContext(Dispatchers.IO) {
        val b = brand.trim()
        val m = model.trim()
        val colorEn = translateColorToEn(color.trim())
        val yearStr = if (year > 1900) "$year" else ""

        val queryFull = buildQuery(yearStr, b, m, colorEn, "car exterior")
        val queryShort = "$b $m car exterior"
        val queryColor = buildQuery(yearStr, b, m, color.trim(), "фото")

        // Собираем кандидатов из всех источников, пропускаем уже показанные
        val candidates = mutableListOf<String>()
        // Google Custom Search API — первым (самый надёжный)
        listOf(
            { tryGoogleCustomSearchApi(queryFull, skipUrls) },
            { tryGoogleCustomSearchApi(queryShort, skipUrls) },
            { tryBingImages(queryFull, skipUrls) },
            { tryBingImages(queryShort, skipUrls) },
            { tryYandexImages(queryColor, skipUrls) },
            { tryYandexImages(queryShort, skipUrls) },
            { tryGoogleImages(queryFull, skipUrls) },
            { tryGoogleImages(queryShort, skipUrls) }
        ).forEach { fn -> fn()?.let { candidates.add(it) } }

        // Возвращаем первый не-пропущенный результат
        candidates.firstOrNull { it !in skipUrls }
    }

    private fun buildQuery(vararg parts: String): String =
        parts.filter { it.isNotBlank() }.joinToString(" ")

    // ── Google Custom Search JSON API ─────────────────────────────────────

    private fun tryGoogleCustomSearchApi(query: String, skip: Set<String> = emptySet()): String? {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.googleapis.com/customsearch/v1" +
                "?key=$GOOGLE_API_KEY&cx=$GOOGLE_CX&q=$encoded&searchType=image&num=10&safe=active"
            val json = get(url) ?: return null

            // Парсим "link": "URL" из ответа API
            val linkPattern = Regex(""""link"\s*:\s*"(https?://[^"]{20,}\.(?:jpg|jpeg|png))"""")
            for (match in linkPattern.findAll(json)) {
                val imgUrl = match.groupValues[1]
                if (imgUrl !in skip && isValidPhoto(imgUrl)) return imgUrl
            }
            null
        } catch (e: Exception) { null }
    }

    // ── Bing Images ───────────────────────────────────────────────────────

    private fun tryBingImages(query: String, skip: Set<String> = emptySet()): String? {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val html = get(
                url = "https://www.bing.com/images/search?q=$encoded&form=HDRSC3&first=1",
                referer = "https://www.bing.com/"
            ) ?: return null

            // Паттерн 1: m='{"murl":"URL"' — основной формат Bing Images
            val murlPattern = Regex(""""murl"\s*:\s*"(https?://[^"]{20,}\.(?:jpg|jpeg|png))"""")
            for (match in murlPattern.findAll(html)) {
                val url = match.groupValues[1]
                if (url !in skip && isValidPhoto(url)) return url
            }

            // Паттерн 2: mediaurl=URL& — старый формат Bing
            val mediaPattern = Regex("""mediaurl=(https?[^&"]{20,}\.(?:jpg|jpeg|png))""")
            for (match in mediaPattern.findAll(html)) {
                val url = URLDecoder(match.groupValues[1])
                if (url !in skip && isValidPhoto(url)) return url
            }

            null
        } catch (e: Exception) { null }
    }

    private fun URLDecoder(s: String): String =
        try { java.net.URLDecoder.decode(s, "UTF-8") } catch (_: Exception) { s }

    // ── Яндекс.Картинки ──────────────────────────────────────────────────

    private fun tryYandexImages(query: String, skip: Set<String> = emptySet()): String? {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val html = get(
                url = "https://yandex.ru/images/search?text=$encoded&itype=photo",
                referer = "https://yandex.ru/"
            ) ?: return null

            // Паттерны в порядке надёжности
            val patterns = listOf(
                // Оригинальный URL в поле "url" JSON-блока
                Regex(""""url"\s*:\s*"(https?://(?!avatars)[^"]{20,}\.(?:jpg|jpeg|png))""""),
                // Поле origin содержит оригинал
                Regex(""""origin"\s*:\s*\{\s*"url"\s*:\s*"(https?://[^"]{20,}\.(?:jpg|jpeg|png))""""),
                // data-src в img тегах
                Regex("""<img[^>]+data-src="(https?://[^"]{20,}\.(?:jpg|jpeg|png))""""),
            )

            for (pattern in patterns) {
                for (match in pattern.findAll(html)) {
                    val url = match.groupValues[1]
                    if (url !in skip && isValidPhoto(url)) return url
                }
            }
            null
        } catch (e: Exception) { null }
    }

    // ── Google Картинки ───────────────────────────────────────────────────

    private fun tryGoogleImages(query: String, skip: Set<String> = emptySet()): String? {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val html = get(
                url = "https://www.google.com/search?q=$encoded&tbm=isch&num=10&hl=en",
                referer = "https://www.google.com/"
            ) ?: return null

            // Google Images: поле "ou" = originalUrl
            val ouPattern = Regex(""""ou"\s*:\s*"(https?://(?!encrypted-tbn)[^"]{20,}\.(?:jpg|jpeg|png))"""")
            for (match in ouPattern.findAll(html)) {
                val url = match.groupValues[1]
                if (url !in skip && isValidPhoto(url)) return url
            }
            null
        } catch (e: Exception) { null }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun isValidPhoto(url: String): Boolean {
        if (!url.startsWith("http")) return false
        if (url.length < 25) return false
        val lower = url.lowercase()
        if (LOGO_KEYWORDS.any { lower.contains(it) }) return false
        if (lower.contains("encrypted-tbn")) return false
        if (lower.contains("gstatic.com/images/branding")) return false
        if (lower.contains("wikimedia.org")) return false
        if (lower.contains("wikipedia.org")) return false
        return true
    }

    private fun translateColorToEn(color: String): String = when (color.lowercase()) {
        "красный", "red"              -> "red"
        "синий", "blue"               -> "blue"
        "чёрный", "черный", "black"   -> "black"
        "белый", "white"              -> "white"
        "серый", "grey", "gray"       -> "grey"
        "серебристый", "silver"       -> "silver"
        "зелёный", "зеленый", "green" -> "green"
        "жёлтый", "желтый", "yellow"  -> "yellow"
        "оранжевый", "orange"         -> "orange"
        "коричневый", "brown"         -> "brown"
        else                          -> ""
    }

    private fun get(url: String, referer: String = ""): String? {
        return try {
            val req = Request.Builder().url(url).apply {
                if (referer.isNotEmpty()) header("Referer", referer)
            }.build()
            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            }
        } catch (e: Exception) { null }
    }
}
