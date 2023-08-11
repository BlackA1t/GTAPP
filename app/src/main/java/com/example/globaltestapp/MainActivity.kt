package com.example.globaltestapp

import DataActivity
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.globaltestapp.R
import com.example.globaltestapp.WifiScanHelper
import android.content.BroadcastReceiver
import android.content.Intent
import android.net.Network
import android.net.NetworkRequest
import android.net.wifi.SupplicantState
import android.net.wifi.WifiNetworkSpecifier
import androidx.core.content.ContextCompat
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket


// Константа для поиска сетей начинающихся со следующих слов
const val WiFiStartWith = ""

// Класс активности (Activity) MainActivity, наследуется от AppCompatActivity
class MainActivity : AppCompatActivity() {

    // Объявление переменных класса
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var wifiScanHelper: WifiScanHelper
    private var connectedNetworkSSID: String? = null
    private lateinit var wifiManager: WifiManager
    private lateinit var wifiListView: ListView

    // Ленивая инициализация адаптера для отображения результатов сканирования
    private val scanResultsAdapter by lazy {
        ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf<String>())
    }

    // Константа для кода запроса разрешений (любое уникальное число)
    companion object {
        private const val PERMISSION_REQUEST_CODE = 77577 // Используем 77577, можно выбрать любое другое уникальное число
    }

    // Метод, вызываемый при создании активности
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Проверяем наличие разрешений при запуске приложения
        if (!isWifiPermissionGranted() || !isLocationPermissionGranted()) {
            requestPermissions()
        }

        // Получение экземпляра менеджера Wi-Fi
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        // Нахождение списка ListView по его идентификатору в макете
        wifiListView = findViewById(R.id.listView)
        // Создание экземпляра класса WifiScanHelper для управления сканированием Wi-Fi
        wifiScanHelper = WifiScanHelper(this)

        // Установка адаптера для отображения результатов сканирования в ListView
        wifiListView.adapter = scanResultsAdapter

        // Установка обработчика клика на кнопку для начала сканирования Wi-Fi сетей
        findViewById<Button>(R.id.button).setOnClickListener {
            checkWifiPermissionAndStartScan()
            // Вызов команды Synhr и обработка ответа
            val response = sendTcpCommand("Synhr")
            checkSynhrResponse(response)
        }

        // Запуск функции обновления текущей подключенной Wi-Fi сети
        startUpdatingConnectedWifi()
        // Запуск функции автоматического сканирования Wi-Fi сетей
        startAutomaticWifiScan()
    }

    // Функция для проверки ответа на команду Synhr и открытия DataActivity в случае успешного ответа
    private fun checkSynhrResponse(response: String?) {
        if (response == "OK") {
            val intent = Intent(this, DataActivity::class.java)
            startActivity(intent)
        } else {
            Toast.makeText(this, "Ошибка при выполнении команды Synhr", Toast.LENGTH_SHORT).show()
        }
    }

    // Функция сканирует все доступные wi-fi сети каждую 1 секунду обновляется
    private fun startAutomaticWifiScan() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                startWifiScan()
                handler.postDelayed(this, 1000)
            }
        }, 1000)
    }

    // Функция обновляет текущую подключенную сеть каждую 1 секунду обновляется
    private fun startUpdatingConnectedWifi() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                updateConnectedWifi()
                handler.postDelayed(this, 1000)
            }
        }, 1000)
    }

    // Функция обновляет информацию о текущей подключенной Wi-Fi сети
    private fun updateConnectedWifi() {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val connectionInfo = wifiManager.connectionInfo
        val connectedWifi = connectionInfo.ssid

        val textViewConnectedWifi = findViewById<TextView>(R.id.textView2)
        if (connectedWifi != null && connectedWifi.isNotEmpty() && connectedWifi != "<unknown ssid>") {
            // Удаление кавычек, если они есть в названии сети
            connectedNetworkSSID = connectedWifi.removeSurrounding("\"")
            textViewConnectedWifi.text = connectedNetworkSSID
        } else {
            connectedNetworkSSID = null
            textViewConnectedWifi.text = "Не подключено"
        }
    }

    // Функция проверяет наличие разрешений и начинает сканирование Wi-Fi сетей
    private fun checkWifiPermissionAndStartScan() {
        if (isWifiPermissionGranted() && isLocationPermissionGranted()) {
            startWifiScan()
            // Вызов команды Synhr и обработка ответа
            val response = sendTcpCommand("Synhr")
            checkSynhrResponse(response)
        } else {
            requestPermissions()
        }
    }


    // Функция проверяет, предоставлено ли разрешение на доступ к Wi-Fi
    private fun isWifiPermissionGranted(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    // Функция проверяет, предоставлено ли разрешение на определение местоположения
    private fun isLocationPermissionGranted(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    // Функция запрашивает необходимые разрешения
    private fun requestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (!isWifiPermissionGranted()) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (!isLocationPermissionGranted()) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        // Запрос разрешений с помощью ActivityCompat.requestPermissions()
        ActivityCompat.requestPermissions(
            this,
            permissionsToRequest.toTypedArray(),
            PERMISSION_REQUEST_CODE // Здесь нужно определить уникальный код запроса разрешений
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // Проверка результатов запроса разрешений
            val allPermissionsGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allPermissionsGranted) {
                startWifiScan() // Если все разрешения предоставлены, начинаем сканирование
            } else {
                Toast.makeText(
                    this,
                    "Не удалось получить все необходимые разрешения",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }


    // Функция начинает сканирование Wi-Fi сетей
    private fun startWifiScan() {
        wifiScanHelper.startScanWifiNetworks { scanResults ->
            displayScanResults(scanResults)
        }
    }

    // Функция отображает результаты сканирования Wi-Fi сетей в ListView
    private fun displayScanResults(scanResults: List<ScanResult>) {
        scanResultsAdapter.clear()

        // Фильтрация и добавление результатов сканирования в адаптер
        for (result in scanResults) {
            if (result.SSID.startsWith(WiFiStartWith, ignoreCase = true)) {
                scanResultsAdapter.add("${result.SSID}, Потери: ${result.level * -1}")
            }
        }

        // Уведомление адаптера об изменении данных
        scanResultsAdapter.notifyDataSetChanged()

        // Установка обработчика клика на элемент списка
        wifiListView.setOnItemClickListener { _, _, position, _ ->
            val selectedResult = scanResults[position]
            val selectedSSID = selectedResult.SSID.removeSurrounding("\"")

            if (selectedSSID == connectedNetworkSSID) {
                // Если выбрана текущая подключенная сеть, ничего не делаем
                return@setOnItemClickListener
            }

            // Подключение к выбранной сети
            connectToNetwork(selectedResult)
        }
    }

    private fun connectToNetwork(selectedResult: ScanResult) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Если устройство использует Android 10+ (SDK 29+)
            val specifier = WifiNetworkSpecifier.Builder()
                .setSsid(selectedResult.SSID)
                .build()

            val networkRequest = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(specifier)
                .build()

            val connectivityManager =
                getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            val networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    // Успешное подключение к сети
                    Toast.makeText(
                        this@MainActivity,
                        "Подключение к сети \"${selectedResult.SSID}\"...",
                        Toast.LENGTH_LONG
                    ).show()
                    connectivityManager.bindProcessToNetwork(network)
                }

                override fun onUnavailable() {
                    // Подключение не удалось
                    Toast.makeText(
                        this@MainActivity,
                        "Не удалось подключиться к сети \"${selectedResult.SSID}\"",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            connectivityManager.requestNetwork(networkRequest, networkCallback)
        } else {
            // Если устройство использует Android 8 (SDK 26) или 9 (SDK 28)
            val wifiConfiguration = getWifiConfiguration(selectedResult)

            if (wifiConfiguration != null) {
                // Подключаемся к сети, если она была сохранена на устройстве
                val networkId = wifiConfiguration.networkId

                if (networkId != -1) {
                    // Отключаем текущее подключение Wi-Fi
                    wifiManager.disconnect()

                    // Подключаемся к выбранной сети
                    val connected = wifiManager.enableNetwork(networkId, true)

                    if (connected) {
                        // Успешное подключение, проверяем доступность интернета
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (isInternetAvailable()) {
                                wifiManager.reconnect()
                                Toast.makeText(
                                    this,
                                    "Подключение к сети \"${selectedResult.SSID}\"...",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                // Ошибка: нет доступа к интернету
                                Toast.makeText(
                                    this,
                                    "Нет доступа к интернету после подключения к сети \"${selectedResult.SSID}\"",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }, 5000) // Пауза для ожидания подключения к сети
                    } else {
                        // Сеть не удалось подключиться, выводим сообщение об ошибке
                        Toast.makeText(
                            this,
                            "Не удалось подключиться к сети \"${selectedResult.SSID}\"",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    // Сеть не удалось добавить, выводим сообщение об ошибке
                    Toast.makeText(
                        this,
                        "Не удалось подключиться к сети \"${selectedResult.SSID}\"",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                // Конфигурация Wi-Fi равна null, выводим сообщение об ошибке
                Toast.makeText(
                    this,
                    "Конфигурация Wi-Fi для сети \"${selectedResult.SSID}\" не найдена",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }





        // Функция проверяет доступность интернета
    private fun isInternetAvailable(): Boolean {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val networkCapabilities = connectivityManager.activeNetwork ?: return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(networkCapabilities)
            return activeNetwork?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } else {
            // For devices below API level 29, use deprecated method
            val activeNetworkInfo = connectivityManager.activeNetworkInfo
            return activeNetworkInfo?.isConnected == true
        }
    }

    // Функция получает конфигурацию Wi-Fi сети, если она уже сохранена на устройстве
    private fun getWifiConfiguration(selectedResult: ScanResult): WifiConfiguration? {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q // For API level 28 and below, permission is not required.
        ) {
            val existingNetwork = wifiManager.configuredNetworks?.firstOrNull { config ->
                config.SSID == "\"" + selectedResult.SSID + "\""
            }

            return existingNetwork ?: createWifiConfiguration(selectedResult)
        } else {
            // If permission to access Wi-Fi is missing, request it.
            requestWifiPermission()
            return null
        }
    }

    // Функция создает конфигурацию Wi-Fi сети с предполагаемым использованием WPA2 безопасности
    private fun createWifiConfiguration(selectedResult: ScanResult): WifiConfiguration {
        val wifiConfig = WifiConfiguration()
        wifiConfig.SSID = "\"" + selectedResult.SSID + "\""

        // Assuming the network has a password, use WPA2 security
        wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
        wifiConfig.preSharedKey = "\"YourNetworkPassword\""

        return wifiConfig
    }

    // Функция для отправки команды на сервер и получения ответа
    private fun sendTcpCommand(command: String): String? {
        val ipAddress = "192.168.12.100"
        val port = 30000
        var response: String? = null

        try {
            val socket = Socket(ipAddress, port)
            val outputStream = OutputStreamWriter(socket.getOutputStream())
            val inputStream = BufferedReader(InputStreamReader(socket.getInputStream()))

            outputStream.write(command)
            outputStream.flush()

            response = inputStream.readLine()

            outputStream.close()
            inputStream.close()
            socket.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return response
    }

    // Функция запрашивает разрешение на доступ к Wi-Fi
    private fun requestWifiPermission() {
        val wifiPermission = Manifest.permission.CHANGE_WIFI_STATE
        val locationPermission = Manifest.permission.ACCESS_FINE_LOCATION

        val permissionsToRequest = ArrayList<String>()

        if (ContextCompat.checkSelfPermission(this, wifiPermission) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(wifiPermission)
        }

        if (ContextCompat.checkSelfPermission(this, locationPermission) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(locationPermission)
        }

        if (permissionsToRequest.isNotEmpty()) {
            val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                val allPermissionsGranted = permissions.all { it.value }
                if (allPermissionsGranted) {
                    startWifiScan()
                } else {
                    Toast.makeText(this, "Не удалось получить разрешение на сканирование Wi-Fi и доступ к местоположению", Toast.LENGTH_SHORT).show()
                }
            }

            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            // Если оба разрешения уже предоставлены, выполняем сканирование Wi-Fi
            startWifiScan()
        }
    }

}
