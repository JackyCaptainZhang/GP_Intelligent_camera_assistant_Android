package com.example.gp_intelligent_camera_assistant

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID



object BluetoothHelper {
    lateinit var socket: BluetoothSocket
    private lateinit var mySelectedBluetoothDevice: BluetoothDevice
    lateinit var MY_UUID : UUID
    var connected : Boolean = false
    lateinit var outputStream: OutputStream
    lateinit var inputStream: InputStream
    private var context: Context? = null
    private lateinit var androidBluetoothManager: android.bluetooth.BluetoothManager
    private var bluetoothAdapter: BluetoothAdapter? = null

    fun init(context: Context) {
        androidBluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
        bluetoothAdapter = androidBluetoothManager.adapter
    }


    @SuppressLint("MissingPermission", "ServiceCast")
    fun connectTOBluetooth(){
        MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // SSP UUID
        val bondedDevices: Set<BluetoothDevice> = bluetoothAdapter!!.bondedDevices // find the bonded device
        bondedDevices?.forEach { device ->
            if (device.address == "98:D3:61:F6:B9:67") { // Find the bonded HC-05 module
                mySelectedBluetoothDevice = device
            }
        }
        socket = mySelectedBluetoothDevice.createRfcommSocketToServiceRecord(MY_UUID) // define the device to the socket
        try {
            socket.connect() // connect to the socket
            connected = true
            outputStream = socket.outputStream // the output stream
            inputStream = socket.inputStream

        } catch (e: IOException){
        }
    }
    fun sendBluetoothCommand(sentCMD: String){
        val dataToSend = sentCMD + "\n" // Must have a "\n" at the end of the message
        val bytes = dataToSend.toByteArray() // change the message to the Byte for serial transmission
        outputStream.write(bytes) // write the message to the output stream
    }

    fun receiveBluetoothCommand(){
        val buffer = ByteArray(1024)
        val bytesRead: Int = inputStream.read(buffer)
        val receivedData = kotlin.String()
    }


}