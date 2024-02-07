package com.example.gp_intelligent_camera_assistant

import android.app.Service
import android.app.Service.START_NOT_STICKY
import android.content.Intent
import android.os.IBinder
import android.util.Log
import java.io.IOException

class BluetoothService : Service(){
    private lateinit var bluetoothThread: Thread

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        bluetoothThread = Thread(Runnable {
            try {
                val stringBuilder = StringBuilder()

                while (true) {
                    val buffer = ByteArray(1024)
                    val bytesRead = BluetoothHelper.inputStream.read(buffer)
                    if (bytesRead > 0) {
                        val receivedData = String(buffer, 0, bytesRead)
                        stringBuilder.append(receivedData)

                        // check "\n"
                        val data = stringBuilder.toString()
                        if (data.contains("\n")) {
                            val parts = data.split("\n") // use "\n" to split the data
                            parts.filter { it.isNotEmpty() }.forEach { part ->
                                handleReceivedData(part) // Check the CMD received
                                Log.d("Receive", part)
                            }
                            stringBuilder.setLength(0)
                        }
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        })
        bluetoothThread.start()
        return START_NOT_STICKY
    }

    private fun handleReceivedData(receivedData: String) { // set up the Broadcast for different commands
        if (receivedData.contains("Find!")) {
            val intent = Intent("Bluetooth.Find_CMD_RECEIVED")
            sendBroadcast(intent)
            Log.d("Sent", "Find sent.")
        }
        if (receivedData.contains("Take photo!")) {
            val intent = Intent("Bluetooth.Take_photo_CMD_RECEIVED")
            sendBroadcast(intent)
            Log.d("Sent", "Take photo sent.")
        }
        if (receivedData.contains("Album!")) {
            val intent = Intent("Bluetooth.Album_CMD_RECEIVED")
            sendBroadcast(intent)
            Log.d("Sent", "Album sent.")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothThread.interrupt() // 确保在服务停止时中断线程
    }
}