package com.example.gp_intelligent_camera_assistant

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.TextureView
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat


class MainActivity : AppCompatActivity() {
    lateinit var cameraDevice: CameraDevice
    lateinit var handler: Handler
    lateinit var cameraManager: CameraManager
    lateinit var textureView: TextureView
    private lateinit var commandReceiver: BroadcastReceiver
    private lateinit var intent: Intent


    @SuppressLint("ServiceCast", "MissingPermission", "UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Start the bluetooth service
        val BluetoothConnectionServiceIntent = Intent(this, BluetoothConnectionService::class.java)
        startService(BluetoothConnectionServiceIntent)
        setContentView(R.layout.activity_main)
        get_permissions()  //ask for required permission at the launch of the App

        var handlerThread = HandlerThread("videoThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)
        val getPredictionButton: Button = findViewById(R.id.getPredictionButton)
        textureView = findViewById(R.id.textureView)  // texture view for displaying the camera preview
        getPredictionButton.setOnClickListener{
            intent = Intent(this@MainActivity, PredictionActivity::class.java)
            startActivity(intent)
        }

        textureView.surfaceTextureListener = object:TextureView.SurfaceTextureListener{
            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                surface.setDefaultBufferSize(3120, 4160)
                openCamera()  //call methods to open the camera
            }

            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {

            }  // todo

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                return false
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
            }
        }
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager


        // register, monitor the broadcast and trigger the functions
        commandReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    "Bluetooth.Find_CMD_RECEIVED" -> runOnUiThread {
                        action_Find_CMD_RECEIVED()
                    }
                    "Bluetooth.Take_photo_CMD_RECEIVED" -> action_Take_photo_CMD_RECEIVED()
                    "Bluetooth.Album_CMD_RECEIVED" -> action_Album_CMD_RECEIVED()
                }
            }
        }
        IntentFilter().apply {
            addAction("Bluetooth.Find_CMD_RECEIVED")
            addAction("Bluetooth.Take_photo_CMD_RECEIVED")
            addAction("Bluetooth.Album_CMD_RECEIVED")
            registerReceiver(commandReceiver, this)
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(commandReceiver)
        stopService(intent)
    }

    fun get_permissions(){  //ask for permission
        var permissionList = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
            permissionList.add(android.Manifest.permission.CAMERA)
        }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
            permissionList.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
            permissionList.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED){
            permissionList.add(android.Manifest.permission.BLUETOOTH_CONNECT)
        }

        if(permissionList.size > 0){
            requestPermissions(permissionList.toTypedArray(),101)
        }
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {  // If permission is not granted, than ask for permission again
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        grantResults.forEach {
            if(it != PackageManager.PERMISSION_GRANTED){
                get_permissions()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun openCamera(){ // Open camera function

        cameraManager.openCamera(cameraManager.cameraIdList[0], object: CameraDevice.StateCallback(){
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera // define a camera device
                var surfaceTexture = textureView.surfaceTexture  // define a surfaceTexture that a Surface will be attached to it
                var surface = Surface(surfaceTexture)  // define the surface and attach it to surfaceTexture
                var captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)  //define a capture request from camera device
                captureRequest.addTarget(surface)  // attach capture request to the surface

                cameraDevice.createCaptureSession(listOf(surface), object: CameraCaptureSession.StateCallback(){
                    override fun onConfigured(session: CameraCaptureSession) {
                        session.setRepeatingRequest(captureRequest.build(), null, null)
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {

                    }  //todo
                }, handler)
            }

            override fun onDisconnected(camera: CameraDevice) {

            }  // todo

            override fun onError(camera: CameraDevice, error: Int) {

            }  // todo
        },handler)

    }

    // Functions for different broadcasts
    private fun action_Find_CMD_RECEIVED() {
        Toast.makeText(this@MainActivity,"Find received!",Toast.LENGTH_LONG).show()
        val intent = Intent(this@MainActivity, PredictionActivity::class.java)
        startActivity(intent)
    }
    private fun action_Take_photo_CMD_RECEIVED() {
        Toast.makeText(this@MainActivity,"Take photo received!",Toast.LENGTH_LONG).show()
    }

    private fun action_Album_CMD_RECEIVED() {
        Toast.makeText(this@MainActivity,"Album received!",Toast.LENGTH_LONG).show()
    }

}