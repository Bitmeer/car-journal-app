package com.example.carjournal

import android.app.Application
import coil.Coil
import coil.ImageLoader
import okhttp3.OkHttpClient
import org.osmdroid.config.Configuration
import java.util.concurrent.TimeUnit

class CarJournalApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // OSMDroid — инициализация кэша тайлов
        Configuration.getInstance().apply {
            load(this@CarJournalApplication, getSharedPreferences("osm_prefs", MODE_PRIVATE))
            userAgentValue = packageName
        }

        // Coil — глобальный ImageLoader с OkHttpClient.
        // Wikimedia Commons и Wikipedia требуют User-Agent, иначе возвращают 403.
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "CarJournalApp/2.0 (Android; coursework)")
                        .header("Referer", "https://commons.wikimedia.org/")
                        .build()
                )
            }
            .build()

        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .okHttpClient(okHttpClient)
                .crossfade(true)
                .build()
        )
    }
}
