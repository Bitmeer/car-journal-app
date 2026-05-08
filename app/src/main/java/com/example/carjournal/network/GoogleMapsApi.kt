package com.example.carjournal.network

import android.util.Log
import com.example.carjournal.data.model.GasStation
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

object GoogleMapsApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // ── OpenStreetMap Overpass — ближайшие заправки ───────────────────────────
    suspend fun searchNearbyGasStations(lat: Double, lon: Double): List<GasStation> =
        withContext(Dispatchers.IO) {
            val query = """[out:json];(node["amenity"="fuel"](around:5000,$lat,$lon);way["amenity"="fuel"](around:5000,$lat,$lon););out center;"""
            // Несколько зеркал Overpass — пробуем по очереди
            val mirrors = listOf(
                "https://overpass-api.de/api/interpreter",
                "https://overpass.openstreetmap.ru/api/interpreter",
                "https://overpass.kumi.systems/api/interpreter"
            )
            for (url in mirrors) {
                try {
                    val body = postForm(url, query) ?: continue
                    val elements = JSONObject(body).getJSONArray("elements")
                    Log.d("GoogleMapsApi", "Overpass[$url] found ${elements.length()} stations")
                    if (elements.length() == 0) continue
                    val list = mutableListOf<GasStation>()
                    for (i in 0 until elements.length()) {
                        val e = elements.getJSONObject(i)
                        val tags = e.optJSONObject("tags") ?: JSONObject()
                        val stLat = if (e.has("lat")) e.getDouble("lat")
                                    else e.optJSONObject("center")?.getDouble("lat") ?: continue
                        val stLon = if (e.has("lon")) e.getDouble("lon")
                                    else e.optJSONObject("center")?.getDouble("lon") ?: continue
                        list.add(
                            GasStation(
                                id = e.getLong("id"),
                                name = tags.optString("brand", tags.optString("name", "АЗС")),
                                brand = tags.optString("operator", tags.optString("network", "")),
                                lat = stLat,
                                lon = stLon
                            )
                        )
                    }
                    return@withContext list
                } catch (e: Exception) {
                    Log.w("GoogleMapsApi", "Overpass mirror $url failed: ${e.message}")
                }
            }
            Log.e("GoogleMapsApi", "All Overpass mirrors failed")
            emptyList()
        }

    // ── OSRM — ETA ────────────────────────────────────────────────────────────
    suspend fun getEta(
        fromLat: Double, fromLon: Double,
        toLat: Double, toLon: Double
    ): String? = withContext(Dispatchers.IO) {
        val osrmHosts = listOf(
            "routing.openstreetmap.de/routed-car",
            "router.project-osrm.org"
        )
        for (host in osrmHosts) {
            try {
                val url = "https://$host/route/v1/driving/$fromLon,$fromLat;$toLon,$toLat?overview=false"
                val body = get(url) ?: continue
                val json = JSONObject(body)
                if (json.optString("code") != "Ok") continue
                val route = json.getJSONArray("routes").getJSONObject(0)
                val durationSec = route.getDouble("duration").roundToInt()
                val distanceM = route.getDouble("distance").roundToInt()
                val mins = (durationSec / 60.0).roundToInt()
                val km = distanceM / 1000.0
                val distStr = if (km < 1) "${distanceM} м" else "${"%.1f".format(km)} км"
                val timeStr = if (mins < 60) "$mins мин" else "${mins / 60} ч ${mins % 60} мин"
                return@withContext "$timeStr · $distStr"
            } catch (e: Exception) {
                Log.w("GoogleMapsApi", "OSRM ETA $host failed: ${e.message}")
            }
        }
        null
    }

    // ── OSRM — маршрут (polyline) ─────────────────────────────────────────────
    suspend fun getRoute(
        fromLat: Double, fromLon: Double,
        toLat: Double, toLon: Double
    ): List<LatLng> = withContext(Dispatchers.IO) {
        val osrmHosts = listOf(
            "routing.openstreetmap.de/routed-car",
            "router.project-osrm.org"
        )
        for (host in osrmHosts) {
            try {
                val url = "https://$host/route/v1/driving/$fromLon,$fromLat;$toLon,$toLat?overview=full&geometries=polyline"
                val body = get(url) ?: continue
                val json = JSONObject(body)
                if (json.optString("code") != "Ok") continue
                val encoded = json.getJSONArray("routes").getJSONObject(0)
                    .getString("geometry")
                val points = decodePolyline(encoded)
                if (points.isNotEmpty()) return@withContext points
            } catch (e: Exception) {
                Log.w("GoogleMapsApi", "OSRM route $host failed: ${e.message}")
            }
        }
        emptyList()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun get(url: String): String? {
        val resp = client.newCall(Request.Builder().url(url).build()).execute()
        Log.d("GoogleMapsApi", "GET $url -> HTTP ${resp.code}")
        return if (resp.isSuccessful) resp.body?.string() else null
    }

    // Overpass требует form-encoded тело с параметром data=<query>
    private fun postForm(url: String, query: String): String? {
        val formBody = FormBody.Builder().add("data", query).build()
        val req = Request.Builder().url(url).post(formBody).build()
        val resp = client.newCall(req).execute()
        Log.d("GoogleMapsApi", "POST $url -> HTTP ${resp.code}")
        return if (resp.isSuccessful) resp.body?.string() else null
    }

    private fun decodePolyline(encoded: String): List<LatLng> {
        val result = mutableListOf<LatLng>()
        var index = 0; var lat = 0; var lng = 0
        while (index < encoded.length) {
            var shift = 0; var b: Int; var n = 0
            do { b = encoded[index++].code - 63; n = n or ((b and 0x1f) shl shift); shift += 5 } while (b >= 0x20)
            lat += if (n and 1 != 0) (n shr 1).inv() else n shr 1
            shift = 0; n = 0
            do { b = encoded[index++].code - 63; n = n or ((b and 0x1f) shl shift); shift += 5 } while (b >= 0x20)
            lng += if (n and 1 != 0) (n shr 1).inv() else n shr 1
            result.add(LatLng(lat / 1e5, lng / 1e5))
        }
        return result
    }
}
