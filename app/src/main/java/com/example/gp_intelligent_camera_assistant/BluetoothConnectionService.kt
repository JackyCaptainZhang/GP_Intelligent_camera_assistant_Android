package com.example.gp_intelligent_camera_assistant

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

class BluetoothConnectionService : Service() {

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        initBluetoothConnection()
    }

    private fun initBluetoothConnection() {
        try {
            BluetoothInitialiser.init(this)
            BluetoothInitialiser.connectTOBluetooth()
            BluetoothInitialiser.connected = true
            val bluetoothMonitorServiceIntent = Intent(this, BluetoothMonitorService::class.java)
            startService(bluetoothMonitorServiceIntent)
        }catch (e: Exception){
            Log.e("BluetoothConnection", "Connection Service filed", e)
        }
    }
}