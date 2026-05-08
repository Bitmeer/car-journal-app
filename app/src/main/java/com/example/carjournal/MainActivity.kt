package com.example.carjournal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.carjournal.data.db.CarDatabase
import com.example.carjournal.data.model.Car
import com.example.carjournal.data.repository.CarRepository
import com.example.carjournal.ui.screens.CarDetailScreen
import com.example.carjournal.ui.screens.CarListScreen
import com.example.carjournal.ui.screens.MapScreen
import com.example.carjournal.ui.theme.CarJournalTheme
import com.example.carjournal.ui.viewmodel.CarViewModel

/** Вкладки нижней навигации */
enum class MainTab { CARS, MAP }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = CarDatabase.getDatabase(applicationContext)
        val repository = CarRepository(database.carDao())

        setContent {
            CarJournalTheme {
                CarJournalApp(
                    application = application,
                    repository = repository
                )
            }
        }
    }
}

/**
 * Корневой Composable — управляет навигацией.
 *
 * Структура:
 *  ├── Нижняя панель: "Автомобили" / "Карта АЗС"
 *  ├── Вкладка CARS → CarListScreen → (tap) → CarDetailScreen (полный экран, без BottomBar)
 *  └── Вкладка MAP  → MapScreen
 */
@Composable
fun CarJournalApp(
    application: android.app.Application,
    repository: CarRepository
) {
    val viewModel: CarViewModel = viewModel(
        factory = CarViewModel.Factory(application, repository)
    )

    var currentTab by remember { mutableStateOf(MainTab.CARS) }
    var selectedCar by remember { mutableStateOf<Car?>(null) }

    // Если выбран автомобиль — показываем детали на весь экран (без BottomBar)
    if (selectedCar != null) {
        CarDetailScreen(
            viewModel = viewModel,
            onBack = { selectedCar = null }
        )
        return
    }

    // Главный экран с нижней навигацией
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentTab == MainTab.CARS,
                    onClick = { currentTab = MainTab.CARS },
                    icon = { Icon(Icons.Default.DirectionsCar, contentDescription = null) },
                    label = { Text("Автомобили") }
                )
                NavigationBarItem(
                    selected = currentTab == MainTab.MAP,
                    onClick = { currentTab = MainTab.MAP },
                    icon = { Icon(Icons.Default.LocalGasStation, contentDescription = null) },
                    label = { Text("Карта АЗС") }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentTab) {
                MainTab.CARS -> CarListScreen(
                    viewModel = viewModel,
                    onCarClick = { car ->
                        viewModel.selectCar(car.id)
                        selectedCar = car
                    }
                )
                MainTab.MAP -> MapScreen(viewModel = viewModel)
            }
        }
    }
}

