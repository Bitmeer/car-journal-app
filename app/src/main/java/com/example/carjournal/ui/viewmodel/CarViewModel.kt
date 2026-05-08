package com.example.carjournal.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.carjournal.R
import java.io.File
import java.io.FileOutputStream
import com.example.carjournal.ai.ImageGenerationStrategy
import com.example.carjournal.ai.NetworkCarImageGenerator
import com.example.carjournal.data.model.Car
import com.example.carjournal.data.model.GasStation
import com.example.carjournal.data.model.MaintenanceRecord
import com.example.carjournal.data.repository.CarRepository
import com.example.carjournal.network.GeminiImageApi
import com.example.carjournal.network.GoogleMapsApi
import com.example.carjournal.network.OverpassApi
import com.example.carjournal.ui.state.ImageState
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ── Состояние экрана карты ──────────────────────────────────────────────────
sealed class MapUiState {
    object Idle : MapUiState()
    object Loading : MapUiState()
    object Success : MapUiState()
    data class Error(val message: String) : MapUiState()
}

/**
 * Единственная ViewModel приложения.
 * Наследует AndroidViewModel для доступа к Application-контексту (нужен OSMDroid).
 *
 * Отвечает за:
 *  — CRUD операции с автомобилями и записями ТО (Room)
 *  — Поиск изображения авто через Wikipedia API (NetworkCarImageGenerator)
 *  — Загрузку ближайших АЗС через Overpass API (OpenStreetMap)
 */
class CarViewModel(
    application: Application,
    private val repository: CarRepository,
    private val imageGenerator: ImageGenerationStrategy = NetworkCarImageGenerator()
) : AndroidViewModel(application) {

    // ─────────────────────────────────────────────────────────────────────────
    //  Список автомобилей
    // ─────────────────────────────────────────────────────────────────────────

    val cars: StateFlow<List<Car>> = repository.getAllCars()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addCar(brand: String, model: String, year: Int, color: String) {
        viewModelScope.launch {
            repository.insertCar(Car(brand = brand, model = model, year = year, color = color))
        }
    }

    fun deleteCar(car: Car) {
        viewModelScope.launch { repository.deleteCar(car) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Выбранный автомобиль и его записи ТО
    // ─────────────────────────────────────────────────────────────────────────

    private val _selectedCarId = MutableStateFlow<Int?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedCar: StateFlow<Car?> = _selectedCarId
        .filterNotNull()
        .flatMapLatest { repository.getCarById(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val maintenanceRecords: StateFlow<List<MaintenanceRecord>> = _selectedCarId
        .filterNotNull()
        .flatMapLatest { repository.getRecordsForCar(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectCar(carId: Int) {
        _selectedCarId.value = carId
        // Загружаем кешированное изображение с диска (если есть)
        val file = File(getApplication<Application>().filesDir, "car_$carId.png")
        if (file.exists()) {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap != null) {
                _imageState.value = ImageState.GeneratedBitmap(bitmap)
                return
            }
        }
        _imageState.value = ImageState.Idle
    }

    fun addRecord(date: String, mileage: Int, cost: Double, workType: String) {
        val carId = _selectedCarId.value ?: return
        viewModelScope.launch {
            repository.insertRecord(
                MaintenanceRecord(
                    carId = carId,
                    date = date,
                    mileage = mileage,
                    cost = cost,
                    workType = workType
                )
            )
        }
    }

    fun deleteRecord(record: MaintenanceRecord) {
        viewModelScope.launch { repository.deleteRecord(record) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Поиск изображения автомобиля в интернете
    // ─────────────────────────────────────────────────────────────────────────

    private val _imageState = MutableStateFlow<ImageState>(ImageState.Idle)
    val imageState: StateFlow<ImageState> = _imageState.asStateFlow()

    // Уже показанные URL — чтобы «найти другое фото» не возвращало то же самое
    private val _shownImageUrls = mutableSetOf<String>()

    /**
     * Ищет изображение автомобиля по марке и модели через Wikipedia API.
     * При отсутствии результата показывает локальный drawable-placeholder.
     * При ошибке сети показывает [ImageState.Error].
     */
    fun searchCarImage(car: Car) {
        viewModelScope.launch {
            _imageState.value = ImageState.Loading
            try {
                val bitmap = GeminiImageApi.generateCarImage(
                    brand = car.brand,
                    model = car.model,
                    color = car.color,
                    year = car.year
                )
                if (bitmap != null) {
                    // Сохраняем на диск (PNG), чтобы не генерировать повторно
                    try {
                        val file = File(getApplication<Application>().filesDir, "car_${car.id}.png")
                        FileOutputStream(file).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                    } catch (_: Exception) {}
                    _imageState.value = ImageState.GeneratedBitmap(bitmap)
                } else {
                    _imageState.value = ImageState.Error("Не удалось сгенерировать изображение")
                }
            } catch (e: Exception) {
                _imageState.value = ImageState.Error(
                    "Ошибка генерации: ${e.message}"
                )
            }
        }
    }

    /** Вспомогательная: возвращает id drawable-заглушки по цвету автомобиля */
    fun getLocalFallbackDrawable(color: String): Int {
        return when (color.lowercase().trim()) {
            "красный", "red"                   -> R.drawable.car_red
            "синий", "blue"                    -> R.drawable.car_blue
            "чёрный", "черный", "black"        -> R.drawable.car_black
            "белый", "white"                   -> R.drawable.car_white
            "серый", "серебристый", "grey",
            "gray", "silver"                   -> R.drawable.car_grey
            else                               -> R.drawable.car_default
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Карта АЗС (Overpass API + OSMDroid)
    // ─────────────────────────────────────────────────────────────────────────

    private val _gasStations = MutableStateFlow<List<GasStation>>(emptyList())
    val gasStations: StateFlow<List<GasStation>> = _gasStations.asStateFlow()

    private val _selectedStation = MutableStateFlow<GasStation?>(null)
    val selectedStation: StateFlow<GasStation?> = _selectedStation.asStateFlow()

    private val _eta = MutableStateFlow<String?>(null)
    val eta: StateFlow<String?> = _eta.asStateFlow()

    private val _routePoints = MutableStateFlow<List<LatLng>>(emptyList())
    val routePoints: StateFlow<List<LatLng>> = _routePoints.asStateFlow()

    // lat/lon пользователя (не используется в WebView-карте, сохраняем для совместимости)
    private val _userLatLon = MutableStateFlow<Pair<Double, Double>?>(null)

    private val _mapState = MutableStateFlow<MapUiState>(MapUiState.Idle)
    val mapState: StateFlow<MapUiState> = _mapState.asStateFlow()

    /**
     * Загружает список ближайших АЗС через Overpass API.
     * Вызывается из MapScreen после получения геолокации.
     */
    fun loadNearbyGasStations(lat: Double, lon: Double) {
        viewModelScope.launch {
            _mapState.value = MapUiState.Loading
            _userLatLon.value = Pair(lat, lon)
            try {
                val stations = GoogleMapsApi.searchNearbyGasStations(lat, lon)
                android.util.Log.d("CarViewModel", "loadNearbyGasStations: got ${stations.size} stations for ($lat, $lon)")
                _gasStations.value = stations
                _mapState.value = if (stations.isEmpty()) {
                    MapUiState.Error("АЗС в радиусе 5 км не найдены")
                } else {
                    MapUiState.Success
                }
            } catch (e: Exception) {
                _mapState.value = MapUiState.Error(
                    "Ошибка загрузки АЗС: ${e.message ?: "нет соединения"}"
                )
            }
        }
    }

    fun selectStation(station: GasStation?) {
        _selectedStation.value = station
        _eta.value = null
        _routePoints.value = emptyList()
    }

    fun loadEta(userLat: Double, userLon: Double, station: GasStation) {
        viewModelScope.launch {
            _eta.value = GoogleMapsApi.getEta(userLat, userLon, station.lat, station.lon)
        }
    }

    fun loadRoute(userLat: Double, userLon: Double, station: GasStation) {
        viewModelScope.launch {
            _routePoints.value = GoogleMapsApi.getRoute(userLat, userLon, station.lat, station.lon)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Factory
    // ─────────────────────────────────────────────────────────────────────────

    class Factory(
        private val application: Application,
        private val repository: CarRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            return CarViewModel(application, repository) as T
        }
    }
}

