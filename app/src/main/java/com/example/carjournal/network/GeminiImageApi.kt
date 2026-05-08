package com.example.carjournal.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

object GeminiImageApi {

    // TODO: вставь свой ключ Vertex AI (Gemini/Imagen)
    // Получить: https://console.cloud.google.com/apis/credentials
    private const val API_KEY = "YOUR_VERTEX_AI_API_KEY"
    private const val PROJECT_ID = "YOUR_GCP_PROJECT_ID"

    // Бесплатный ключ для удаления фона (50 фото/мес):
    // 1. Зайди на https://www.remove.bg/api
    // 2. Нажми "Get API Key Free"
    // 3. Вставь ключ сюда:
    private const val REMOVEBG_KEY = "YOUR_REMOVEBG_API_KEY"

    private const val VERTEX_URL =
        "https://us-central1-aiplatform.googleapis.com/v1/projects/$PROJECT_ID/locations/us-central1/publishers/google/models/imagen-3.0-generate-001:predict?key=$API_KEY"
    private const val REMOVEBG_URL = "https://api.remove.bg/v1.0/removebg"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private fun translateColorToEn(color: String): String = when (color.trim().lowercase()) {
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
        else                          -> color.trim()
    }

    suspend fun generateCarImage(
        brand: String,
        model: String,
        color: String = "",
        year: Int = 0
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val colorEn = translateColorToEn(color)
            val colorPart = if (colorEn.isNotBlank()) "$colorEn " else ""
            val yearPart = if (year > 1900) " $year" else ""

            val prompt = "professional product photo of a ${colorPart}${brand} ${model}${yearPart} car, " +
                "left side profile, car facing right, full car visible, " +
                "automotive catalog photography, ultra realistic, sharp details, high resolution"

            val jsonBody = """
                {
                  "instances": [{"prompt": ${escapeJson(prompt)}}],
                  "parameters": {
                    "sampleCount": 1,
                    "aspectRatio": "16:9"
                  }
                }
            """.trimIndent()

            val request = Request.Builder()
                .url(VERTEX_URL)
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .header("Content-Type", "application/json")
                .build()

            var bitmap: Bitmap? = null

            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string() ?: return@withContext null
                Log.d("GeminiImageApi", "HTTP ${resp.code}")

                if (!resp.isSuccessful) {
                    Log.e("GeminiImageApi", "Vertex error: ${body.take(400)}")
                    return@withContext null
                }

                val base64Pattern = Regex(""""bytesBase64Encoded"\s*:\s*"([A-Za-z0-9+/\r\n]+=*)"""")
                val base64 = base64Pattern.find(body)
                    ?.groupValues?.get(1)
                    ?.replace("\r", "")?.replace("\n", "")

                if (base64 == null) {
                    Log.e("GeminiImageApi", "No image in response: ${body.take(400)}")
                    return@withContext null
                }

                val bytes = Base64.decode(base64, Base64.DEFAULT)
                bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }

            val source = bitmap ?: return@withContext null

            // Зеркалируем — машина смотрит вправо (передок справа)
            val flipped = flipHorizontally(source)
            Log.d("GeminiImageApi", "Flipped OK")

            // Убираем фон через remove.bg, если ключ задан
            if (REMOVEBG_KEY.isNotBlank()) {
                val transparent = removeBackground(flipped)
                if (transparent != null) {
                    Log.d("GeminiImageApi", "Background removed OK")
                    return@withContext transparent
                }
            }

            // Без ключа — просто зеркальная картинка
            flipped
        } catch (e: Exception) {
            Log.e("GeminiImageApi", "Exception: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    private fun flipHorizontally(src: Bitmap): Bitmap {
        val m = Matrix().apply { preScale(-1f, 1f) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    private fun removeBackground(src: Bitmap): Bitmap? {
        return try {
            val baos = ByteArrayOutputStream()
            src.compress(Bitmap.CompressFormat.PNG, 100, baos)
            val pngBytes = baos.toByteArray()

            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "image_file", "car.png",
                    pngBytes.toRequestBody("image/png".toMediaType())
                )
                .addFormDataPart("size", "auto")
                .build()

            val req = Request.Builder()
                .url(REMOVEBG_URL)
                .post(body)
                .header("X-Api-Key", REMOVEBG_KEY)
                .build()

            client.newCall(req).execute().use { resp ->
                Log.d("GeminiImageApi", "remove.bg HTTP ${resp.code}")
                if (!resp.isSuccessful) return null
                val bytes = resp.body?.bytes() ?: return null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (e: Exception) {
            Log.e("GeminiImageApi", "removeBackground: ${e.message}")
            null
        }
    }

    private fun escapeJson(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
