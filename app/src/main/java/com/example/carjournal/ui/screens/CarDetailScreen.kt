package com.example.carjournal.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.carjournal.data.model.MaintenanceRecord
import com.example.carjournal.ui.state.ImageState
import com.example.carjournal.ui.viewmodel.CarViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarDetailScreen(
    viewModel: CarViewModel,
    onBack: () -> Unit
) {
    val car by viewModel.selectedCar.collectAsState()
    val records by viewModel.maintenanceRecords.collectAsState()
    val imageState by viewModel.imageState.collectAsState()
    var showAddRecordDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(car?.let { "${it.brand} ${it.model}" } ?: "Автомобиль") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddRecordDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Добавить запись ТО")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            car?.let { currentCar ->
                item { CarInfoCard(currentCar.brand, currentCar.model, currentCar.year, currentCar.color) }
                item {
                    ImageSearchSection(
                        imageState = imageState,
                        fallbackDrawable = viewModel.getLocalFallbackDrawable(currentCar.color),
                        onSearchClick = { viewModel.searchCarImage(currentCar) }
                    )
                }
                if (records.isNotEmpty()) {
                    item { StatisticsCard(records) }
                }
                item {
                    Text(
                        text = "Записи технического обслуживания",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (records.isEmpty()) {
                    item {
                        Text(
                            "Записей пока нет. Нажмите «+» чтобы добавить.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(records, key = { it.id }) { record ->
                        MaintenanceRecordItem(record = record, onDeleteClick = { viewModel.deleteRecord(record) })
                    }
                }
            }
        }
    }

    if (showAddRecordDialog) {
        AddRecordDialog(
            onDismiss = { showAddRecordDialog = false },
            onConfirm = { date, mileage, cost, workType ->
                viewModel.addRecord(date, mileage, cost, workType)
                showAddRecordDialog = false
            }
        )
    }
}

// ── Карточка инфо об авто ──────────────────────────────────────────────────

@Composable
private fun CarInfoCard(brand: String, model: String, year: Int, color: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("$brand $model", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoChip("Год", "$year")
                InfoChip("Цвет", color)
            }
        }
    }
}

@Composable
private fun InfoChip(label: String, value: String) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Секция поиска изображения в интернете ──────────────────────────────────

@Composable
private fun ImageSearchSection(imageState: ImageState, fallbackDrawable: Int, onSearchClick: () -> Unit) {
    when (imageState) {
        // ── Когда изображение есть — карточка не выделяется, сливается с фоном ──
        is ImageState.GeneratedBitmap -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    bitmap = imageState.bitmap.asImageBitmap(),
                    contentDescription = "Сгенерированное фото автомобиля",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(Color.Transparent)
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "✨ AI Imagen 3",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(onClick = onSearchClick) {
                        Text("Обновить фото", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        // ── Во всех остальных состояниях — стандартная карточка с кнопкой ──
        else -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Изображение автомобиля", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(10.dp))
                    when (imageState) {
                        is ImageState.Idle -> {
                            Button(onClick = onSearchClick, modifier = Modifier.fillMaxWidth()) { Text("Сгенерировать фото AI") }
                            Spacer(Modifier.height(4.dp))
                            Text("Создаёт уникальное фото через Google Imagen 3", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        is ImageState.Loading -> {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(8.dp))
                            Text("Генерация изображения AI...", style = MaterialTheme.typography.bodySmall)
                        }
                        is ImageState.NetworkUrl -> {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(imageState.url)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Фотография автомобиля",
                                contentScale = ContentScale.Fit,
                                placeholder = painterResource(id = fallbackDrawable),
                                error = painterResource(id = fallbackDrawable),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 160.dp, max = 260.dp)
                                    .wrapContentHeight()
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = onSearchClick, modifier = Modifier.fillMaxWidth()) { Text("Найти другое фото") }
                        }
                        is ImageState.LocalFallback -> {
                            Image(
                                painter = painterResource(id = fallbackDrawable),
                                contentDescription = "Заглушка автомобиля",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth().height(160.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("Фото не найдено — попробуйте ещё раз", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            OutlinedButton(onClick = onSearchClick, modifier = Modifier.fillMaxWidth()) { Text("Попробовать снова") }
                        }
                        is ImageState.Error -> {
                            Text(imageState.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = onSearchClick, modifier = Modifier.fillMaxWidth()) { Text("Повторить") }
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}

// ── Карточка статистики ────────────────────────────────────────────────────

@Composable
private fun StatisticsCard(records: List<MaintenanceRecord>) {
    val totalCost = records.sumOf { it.cost }
    val maxMileage = records.maxOf { it.mileage }
    val minMileage = records.minOf { it.mileage }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Статистика", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatItem("Записей", "${records.size}")
                VerticalDivider(modifier = Modifier.height(40.dp))
                StatItem("Расходы", "%.0f ₽".format(totalCost))
                VerticalDivider(modifier = Modifier.height(40.dp))
                StatItem("Пробег за период", "${maxMileage - minMileage} км")
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
    }
}

// ── Карточка одной записи ТО ───────────────────────────────────────────────

@Composable
private fun MaintenanceRecordItem(record: MaintenanceRecord, onDeleteClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(record.workType, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(3.dp))
                Text(
                    "${record.date}  •  ${record.mileage} км  •  ${"%.0f".format(record.cost)} ₽",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// ── Диалог добавления записи ТО ────────────────────────────────────────────

@Composable
private fun AddRecordDialog(onDismiss: () -> Unit, onConfirm: (String, Int, Double, String) -> Unit) {
    var date by remember { mutableStateOf("") }
    var mileageText by remember { mutableStateOf("") }
    var costText by remember { mutableStateOf("") }
    var workType by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить запись ТО") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // ── Дата с автоматической расстановкой точек DD.MM.YYYY ──
                OutlinedTextField(
                    value = date,
                    onValueChange = { input ->
                        // Оставляем только цифры, не более 8 штук
                        val digits = input.filter { it.isDigit() }.take(8)
                        // Вставляем точки: DD.MM.YYYY
                        date = buildString {
                            digits.forEachIndexed { i, c ->
                                if (i == 2 || i == 4) append('.')
                                append(c)
                            }
                        }
                    },
                    label = { Text("Дата") },
                    placeholder = { Text("ДД.ММ.ГГГГ", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(value = mileageText, onValueChange = { mileageText = it.filter { c -> c.isDigit() } }, label = { Text("Пробег (км)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = costText, onValueChange = { costText = it }, label = { Text("Стоимость (₽)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = workType, onValueChange = { workType = it }, label = { Text("Тип работ (Замена масла, ТО...)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val mileage = mileageText.toIntOrNull() ?: return@TextButton
                val cost = costText.toDoubleOrNull() ?: return@TextButton
                if (date.isNotBlank() && workType.isNotBlank()) onConfirm(date.trim(), mileage, cost, workType.trim())
            }) { Text("Добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
