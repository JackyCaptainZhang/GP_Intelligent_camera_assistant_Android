package com.example.gp_intelligent_camera_assistant

import android.app.IntentService
import android.content.Intent
import android.os.IBinder
import android.util.Log
import java.io.IOException

class BluetoothMonitorService : IntentService("BluetoothMonitorService") {
    private lateinit var bluetoothThread: Thread

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onHandleIntent(intent: Intent?) {
        try {
            val stringBuilder = StringBuilder()

            while (true) {
                if (BluetoothHelper.inputStream != null) {
                    val buffer = ByteArray(1024)
                    val bytesRead = BluetoothHelper.inputStream!!.read(buffer)
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
                } else {
                    Log.e("BluetoothMonitorService", "Input stream is not initialized")
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
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
        if (receivedData.contains("Search finish!")) {
            val intent = Intent("Bluetooth.Search_finish_CMD_RECEIVED")
            sendBroadcast(intent)
            Log.d("Sent", "Search finish sent.")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothThread.interrupt()
    }
}