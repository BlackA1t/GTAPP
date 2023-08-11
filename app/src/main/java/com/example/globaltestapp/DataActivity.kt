import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.globaltestapp.R
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket

class DataActivity : AppCompatActivity() {

    // Здесь можно объявить переменные для хранения полученных значений, если необходимо

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data)

        // Нахождение кнопок по их идентификаторам в макете
        val startButton = findViewById<Button>(R.id.startButton)
        val stopButton = findViewById<Button>(R.id.stopButton)
        val getParButton = findViewById<Button>(R.id.getParButton)
        val setParButton = findViewById<Button>(R.id.setParButton)
        val getMeasButton = findViewById<Button>(R.id.getMeasButton)

        // Нахождение TextView для отображения полученных значений
        val resultTextView = findViewById<TextView>(R.id.resultTextView)

        // Обработка нажатия кнопки Start
        startButton.setOnClickListener {
            val response = sendTcpCommand("Start")
            // Обработка ответа и отображение на экране, если необходимо
        }

        // Обработка нажатия кнопки Stop
        stopButton.setOnClickListener {
            val response = sendTcpCommand("Stop")
            // Обработка ответа и отображение на экране, если необходимо
        }

        // Обработка нажатия кнопки GetPar
        getParButton.setOnClickListener {
            val response = sendTcpCommand("GetPar")
            // Обработка ответа и отображение на экране, если необходимо
        }

        // Обработка нажатия кнопки SetPar
        setParButton.setOnClickListener {
            val response = sendTcpCommand("SetPar")
            // Обработка ответа и отображение на экране, если необходимо
        }

        // Обработка нажатия кнопки GetMeas
        getMeasButton.setOnClickListener {
            val response = sendTcpCommand("GetMeas")
            // Обработка ответа и отображение на экране, если необходимо
        }
    }


    // Функция для установки соединения TCP/IP и отправки команды на сервер
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
}
