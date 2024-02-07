package com.example.gp_intelligent_camera_assistant

import android.app.Service
import android.content.Intent
import android.os.IBinder

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
            BluetoothHelper.init(this)
            BluetoothHelper.connectTOBluetooth()
            BluetoothHelper.connected = true
            val bluetoothMonitorServiceIntent = Intent(this, BluetoothMonitorService::class.java)
            startService(bluetoothMonitorServiceIntent)
        }catch (e: Exception){
        }
    }
}