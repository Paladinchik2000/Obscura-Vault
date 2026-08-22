package com.obscura

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.obscura.ui.theme.MyApplicationTheme
import com.obscura.ui.auth.AuthScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    
                    // Box применяет безопасные отступы (innerPadding), чтобы UI не уезжал за края экрана
                    Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                        
                        // Временное хранилище состояния для теста интерфейса
                        var currentPin by remember { mutableStateOf("") }
                        var currentError by remember { mutableStateOf<String?>(null) }

                        AuthScreen(
                            pinInput = currentPin,
                            errorMessage = currentError,
                            onBiometricClick = {
                                // Заглушка: при нажатии на сканер появится этот текст
                                currentError = "Биометрия пока не подключена!"
                            },

                            // ДОБАВЛЯЕМ ЭТОТ БЛОК ДЛЯ КНОПКИ "СТЕРЕТЬ":
                            onPinBackspace = {
                                if (currentPin.isNotEmpty()) {
                                    currentPin = currentPin.dropLast(1) // Удаляем последнюю цифру
                                }
                                if (currentError != null) {
                                    currentError = null // Убираем ошибку, если она была
                                }
                            },
                            // =========================================

                            onPinDigitEntered = { digit ->
                                // Ограничиваем длину ПИН-кода до 6 символов
                                if (currentPin.length < 6) {
                                    currentPin += digit
                                }

                                // Сбрасываем ошибку, если пользователь начал вводить цифры
                                if (currentError != null) {
                                    currentError = null
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}