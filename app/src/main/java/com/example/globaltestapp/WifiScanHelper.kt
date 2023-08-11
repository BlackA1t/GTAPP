package com.example.globaltestapp

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import androidx.core.app.ActivityCompat

// Класс WifiScanHelper, который помогает сканировать Wi-Fi сети
class WifiScanHelper(private val context: Context) {
    // Инициализация объекта WifiManager для управления Wi-Fi
    private val wifiManager: WifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    // Создание списка для хранения результатов сканирования
    private val scanResults: MutableList<ScanResult> = mutableListOf()
    // Callback-функция для передачи результатов сканирования вызывающему коду
    private var scanCallback: ((List<ScanResult>) -> Unit)? = null

    // Создание BroadcastReceiver для прослушивания результатов сканирования Wi-Fi
    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                // При получении результатов сканирования, очищаем список и заполняем его новыми результатами
                scanResults.clear()
                if (ActivityCompat.checkSelfPermission(
                        context!!,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    // Если есть разрешение на доступ к местоположению, добавляем результаты сканирования
                    scanResults.addAll(wifiManager.scanResults)
                } else {
                    // Обработка случая, когда разрешение не предоставлено
                    // Например, вы можете запросить разрешение здесь
                    // или показать сообщение пользователю, объясняющее, почему
                    // результаты сканирования не могут быть получены.
                }
                // Вызываем переданную callback-функцию, передавая ей результаты сканирования
                scanCallback?.invoke(scanResults)
            }
        }
    }

    // Метод для начала сканирования Wi-Fi сетей
    fun startScanWifiNetworks(callback: (List<ScanResult>) -> Unit) {
        // Устанавливаем переданную callback-функцию в поле scanCallback
        scanCallback = callback
        // Создаем IntentFilter для отслеживания действия завершения сканирования Wi-Fi
        val intentFilter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        // Регистрируем BroadcastReceiver для прослушивания результатов сканирования
        context.registerReceiver(scanReceiver, intentFilter)
        // Запускаем сканирование Wi-Fi сетей
        wifiManager.startScan()
    }
}
