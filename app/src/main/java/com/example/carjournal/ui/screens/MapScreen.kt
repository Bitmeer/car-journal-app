package com.example.carjournal.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.carjournal.data.model.GasStation
import com.example.carjournal.ui.viewmodel.CarViewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@SuppressLint("MissingPermission")
@Composable
fun MapScreen(viewModel: CarViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var userLatLng by remember { mutableStateOf(LatLng(55.7558, 37.6173)) }
    var locationReady by remember { mutableStateOf(false) }

    val mapState by viewModel.mapState.collectAsState()
    val gasStations by viewModel.gasStations.collectAsState()
    val selectedStation by viewModel.selectedStation.collectAsState()
    val eta by viewModel.eta.collectAsState()
    val routePoints by viewModel.routePoints.collectAsState()

    var showLayersMenu by remember { mutableStateOf(false) }
    var enabledLayers by remember { mutableStateOf(emptySet<String>()) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(userLatLng, 13f)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        scope.launch {
            val loc = if (granted) getLocation(context) else LatLng(55.7558, 37.6173)
            userLatLng = loc
            locationReady = true
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(loc, 15f))
            viewModel.loadNearbyGasStations(loc.latitude, loc.longitude)
        }
    }

    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            val loc = getLocation(context)
            userLatLng = loc
            locationReady = true
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(loc, 15f))
            viewModel.loadNearbyGasStations(loc.latitude, loc.longitude)
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // Кэшируем bitmap-маркеры ВНЕ GoogleMap-лямбды (remember внутри @GoogleMapComposable forEach — баг)
    val markerNormal = remember { makeGasStationMarker(false) }
    val markerSelected = remember { makeGasStationMarker(true) }

    Box(modifier = Modifier.fillMaxSize()) {

        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false,
                compassEnabled = true
            ),
            properties = MapProperties(
                isMyLocationEnabled = locationReady
            )
        ) {
            // Применяем стиль принудительно через MapEffect (гарантированно работает)
            MapEffect(enabledLayers) { map ->
                map.setMapStyle(buildMapStyle(enabledLayers))
            }
            // Маршрут Polyline
            if (routePoints.size >= 2) {
                Polyline(
                    points = routePoints,
                    color = Color(0xFF1565C0),
                    width = 14f
                )
            }

            // Маркеры АЗС
            gasStations.forEach { station ->
                val isSelected = station.id == selectedStation?.id
                Marker(
                    state = MarkerState(position = LatLng(station.lat, station.lon)),
                    title = station.name.ifBlank { "АЗС" },
                    snippet = station.brand.ifBlank { null },
                    icon = BitmapDescriptorFactory.fromBitmap(
                        if (isSelected) markerSelected else markerNormal
                    ),
                    zIndex = if (isSelected) 2f else 1f,
                    onClick = { _ ->
                        viewModel.selectStation(station)
                        viewModel.loadEta(userLatLng.latitude, userLatLng.longitude, station)
                        true
                    }
                )
            }
        }

        // Бейдж с количеством заправок / состояние загрузки / ошибка с retry
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 16.dp, start = 16.dp)
        ) {
            when (val st = mapState) {
                is com.example.carjournal.ui.viewmodel.MapUiState.Loading -> {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        tonalElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Text("Загрузка АЗС...", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                is com.example.carjournal.ui.viewmodel.MapUiState.Error -> {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.errorContainer,
                        tonalElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                st.message,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            TextButton(
                                onClick = {
                                    if (locationReady) viewModel.loadNearbyGasStations(userLatLng.latitude, userLatLng.longitude)
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                            ) {
                                Text("Повтор", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                is com.example.carjournal.ui.viewmodel.MapUiState.Success -> {
                    if (gasStations.isNotEmpty()) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            tonalElevation = 4.dp
                        ) {
                            Text(
                                text = "⛽ ${gasStations.size} АЗС",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                else -> {}
            }
        }

        // FAB — слои карты (вверху справа)
        FloatingActionButton(
            onClick = { showLayersMenu = !showLayersMenu },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp),
            containerColor = if (showLayersMenu || enabledLayers.isNotEmpty())
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Icon(Icons.Default.Layers, contentDescription = "Слои карты")
        }

        // Backdrop для закрытия меню тапом вне карточки
        if (showLayersMenu) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { showLayersMenu = false }
            )
        }

        // Меню слоёв
        AnimatedVisibility(
            visible = showLayersMenu,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 80.dp, end = 16.dp)
        ) {
            LayersMenu(
                enabledLayers = enabledLayers,
                onToggle = { featureType, enabled ->
                    enabledLayers = if (enabled) enabledLayers + featureType else enabledLayers - featureType
                }
            )
        }

        // FAB — вернуться к позиции пользователя
        FloatingActionButton(
            onClick = {
                if (locationReady) {
                    scope.launch {
                        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(userLatLng, 15f))
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(start = 16.dp, end = 16.dp, bottom = if (selectedStation != null) 160.dp else 16.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Icon(Icons.Default.MyLocation, contentDescription = "Моя позиция")
        }

        // ── Панель выбранной АЗС (ETA + Go) ──────────────────────────────────
        AnimatedVisibility(
            visible = selectedStation != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            selectedStation?.let { station ->
                StationInfoPanel(
                    station = station,
                    eta = eta,
                    onClose = { viewModel.selectStation(null) },
                    onGo = {
                        viewModel.loadRoute(userLatLng.latitude, userLatLng.longitude, station)
                        scope.launch {
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLngZoom(LatLng(station.lat, station.lon), 14f)
                            )
                        }
                    }
                )
            }
        }

        // Оверлей ожидания геолокации
        if (!locationReady) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Card(modifier = Modifier.padding(32.dp)) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Text("Определяем местоположение...")
                    }
                }
            }
        }
    }
}

@Composable
private fun StationInfoPanel(
    station: GasStation,
    eta: String?,
    onClose: () -> Unit,
    onGo: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = station.name.ifBlank { "АЗС" },
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (station.brand.isNotBlank()) {
                        Text(
                            text = station.brand,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Закрыть")
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ETA
                Column(modifier = Modifier.weight(1f)) {
                    Text("В пути", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (eta != null) {
                        Text(eta, style = MaterialTheme.typography.bodyLarge)
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                    }
                }

                // Кнопка Go
                Button(onClick = onGo) {
                    Text("Go", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
private suspend fun getLocation(context: android.content.Context): LatLng {
    val fusedClient = LocationServices.getFusedLocationProviderClient(context)
    val loc = suspendCancellableCoroutine<android.location.Location?> { cont ->
        fusedClient.lastLocation
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resume(null) }
    }
    return if (loc != null) LatLng(loc.latitude, loc.longitude) else LatLng(55.7558, 37.6173)
}

// Рисует кастомный маркер заправки: круг с иконкой ⛽
// Рисует кастомный маркер заправки в форме пина Google Maps
private fun makeGasStationMarker(selected: Boolean): Bitmap {
    val w = 120
    val h = 160
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)

    val cx = w / 2f
    val r = w / 2f - 6f  // радиус круга
    val tipY = h - 4f     // кончик пина

    val mainColor = if (selected)
        android.graphics.Color.rgb(22, 160, 74)   // зелёный
    else
        android.graphics.Color.rgb(230, 81, 0)    // оранжевый

    // Тень
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(50, 0, 0, 0)
    }
    val path = android.graphics.Path().apply {
        addCircle(cx + 3f, r + 9f, r, android.graphics.Path.Direction.CW)
        moveTo(cx - r * 0.4f + 3f, r * 1.5f + 9f)
        lineTo(cx + 3f, tipY + 6f)
        lineTo(cx + r * 0.4f + 3f, r * 1.5f + 9f)
        close()
    }
    canvas.drawPath(path, shadowPaint)

    // Треугольник-хвост
    val tailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = mainColor }
    val tail = android.graphics.Path().apply {
        moveTo(cx - r * 0.45f, r * 1.55f)
        lineTo(cx, tipY)
        lineTo(cx + r * 0.45f, r * 1.55f)
        close()
    }
    canvas.drawPath(tail, tailPaint)

    // Круг фона
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = mainColor }
    canvas.drawCircle(cx, r + 6f, r, bgPaint)

    // Белый контур
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    canvas.drawCircle(cx, r + 6f, r, strokePaint)

    // Белый круг внутри (для иконки)
    val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(50, 255, 255, 255)
    }
    canvas.drawCircle(cx, r + 6f, r * 0.65f, innerPaint)

    // Иконка ⛽
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 44f
        textAlign = Paint.Align.CENTER
    }
    canvas.drawText("⛽", cx, r + 6f + 16f, textPaint)

    return bmp
}

// Строит JSON-стиль карты: всё POI скрыто, кроме выбранных слоёв
private fun buildMapStyle(enabledLayers: Set<String>): MapStyleOptions {
    val rules = mutableListOf(
        """{"featureType":"poi","stylers":[{"visibility":"off"}]}""",
        """{"featureType":"transit","stylers":[{"visibility":"off"}]}"""
    )
    enabledLayers.forEach { featureType ->
        rules.add("""{"featureType":"$featureType","stylers":[{"visibility":"on"}]}""")
    }
    return MapStyleOptions("[${rules.joinToString(",")}]")
}

@Composable
private fun LayersMenu(
    enabledLayers: Set<String>,
    onToggle: (String, Boolean) -> Unit
) {
    val layers = remember {
        listOf(
            Triple("poi.business",     "🏬", "ТЦ и рестораны"),
            Triple("poi.attraction",   "🎭", "Достопримечательности"),
            Triple("poi.park",         "🌳", "Парки"),
            Triple("poi.medical",      "🏥", "Медицина"),
            Triple("poi.school",       "🎓", "Школы и вузы"),
            Triple("transit",          "🚌", "Транспорт"),
        )
    }
    Card(
        elevation = CardDefaults.cardElevation(8.dp),
        modifier = Modifier.widthIn(min = 250.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                text = "Показать на карте",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            layers.forEach { (featureType, emoji, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(featureType, featureType !in enabledLayers) }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(emoji, style = MaterialTheme.typography.bodyLarge)
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(
                        checked = featureType in enabledLayers,
                        onCheckedChange = { onToggle(featureType, it) }
                    )
                }
            }
        }
    }
}
