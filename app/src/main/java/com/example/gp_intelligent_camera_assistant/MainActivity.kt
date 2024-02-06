package com.example.gp_intelligent_camera_assistant

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.gp_intelligent_camera_assistant.BluetoothHelper.connected


class MainActivity : AppCompatActivity() {
    lateinit var cameraDevice: CameraDevice
    lateinit var handler: Handler
    lateinit var cameraManager: CameraManager
    lateinit var textureView: TextureView


    @SuppressLint("ServiceCast", "MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        get_permissions()  //ask for required permission at the launch of the App

        try {
            BluetoothHelper.init(this)
            BluetoothHelper.connectTOBluetooth()
            connected = true
            BluetoothHelper.sendBluetoothCommand("Hello!!!")
            val bluetoothServiceIntent = Intent(this, BluetoothService::class.java)
            startService(bluetoothServiceIntent)
        }catch (e: Exception){
        }

        var handlerThread = HandlerThread("videoThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)
        val getPredictionButton: Button = findViewById(R.id.getPredictionButton)
        textureView = findViewById(R.id.textureView)  // texture view for displaying the camera preview

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


        getPredictionButton.setOnClickListener{
            val intent = Intent(this@MainActivity, PredictionActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onPause() {
        super.onPause()
        val sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putBoolean("isConnected", connected)
        editor.apply()

    }

    override fun onResume() {
        super.onResume()
        val sharedPreferencesRestore = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        val isConnected = sharedPreferencesRestore.getBoolean("isConnected", false)
        if (!isConnected) {
            BluetoothHelper.connectTOBluetooth()
        }
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
    fun openCamera(){

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

}