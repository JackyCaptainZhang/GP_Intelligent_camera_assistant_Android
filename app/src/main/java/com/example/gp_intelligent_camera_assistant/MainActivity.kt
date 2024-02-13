package com.example.gp_intelligent_camera_assistant

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.gp_intelligent_camera_assistant.ml.LiteModelSsdMobilenetV11Metadata2
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.io.IOException


class MainActivity : AppCompatActivity() {
    // System inner unadjustable parameters
    lateinit var cameraDevice: CameraDevice
    lateinit var handler: Handler
    lateinit var cameraManager: CameraManager
    lateinit var textureView: TextureView
    private lateinit var commandReceiver: BroadcastReceiver
    private lateinit var BluetoothConnectionServiceIntent: Intent
    lateinit var bitmap: Bitmap
    lateinit var model: LiteModelSsdMobilenetV11Metadata2
    lateinit var labels:List<String>
    lateinit var imageProcessor: ImageProcessor
    var color = listOf<Int>(
        Color.BLUE, Color.GREEN, Color.RED, Color.CYAN, Color.GRAY, Color.BLACK,
        Color.DKGRAY, Color.MAGENTA, Color.YELLOW)
    val paint = Paint()
    lateinit var imageView: ImageView
    var detectedTimes: Int = 0

    // system adjustable parameters
    var detectedTimes_threashhold = 10 // control the detection FPS
    var clicked: Boolean = false // control the turn on/off of the detection function
    val detection_threashhold: Float = 0.5f // control the detection sensibility
    var detection_item: String = "cup" // control which item to be detected


    @SuppressLint("ServiceCast", "MissingPermission", "UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        get_permissions()  //ask for required permission at the launch of the App
        // label and model definition
        labels = FileUtil.loadLabels(this, "labels.txt")
        imageProcessor = ImageProcessor.Builder().add(ResizeOp(300,300,ResizeOp.ResizeMethod.BILINEAR)).build()  //define the pre-process method
        model = LiteModelSsdMobilenetV11Metadata2.newInstance(this)  // define and initialize the model
        // handler definition
        var handlerThread = HandlerThread("videoThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)
        // UI element definition
        textureView = findViewById(R.id.textureView)  // texture view for displaying the camera preview
        imageView = findViewById(R.id.PredictionimageView)  // image view for displaying the detection result
        imageView.visibility = View.GONE
        //Buttons definition
        val getPredictionButton: Button = findViewById(R.id.getPredictionButton)
        val bluetoothConnectButton: Button = findViewById(R.id.connectButton)

        // prediction button action for test purpose
        getPredictionButton.setOnClickListener{
            clicked = true
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
                if (clicked){
                    imageView.visibility = View.VISIBLE
                    get_Detection(detection_item)
                }else{
                    imageView.visibility = View.GONE
                }
            }
        }
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        // Start the bluetooth service
        try {
            BluetoothConnectionServiceIntent = Intent(this, BluetoothConnectionService::class.java)
            startService(BluetoothConnectionServiceIntent)
        }catch (e: IOException){
            Log.e("BluetoothConnection", "Main connecttion failed", e)
        }

        // register, monitor the broadcast and trigger the functions
        commandReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    "Bluetooth.Find_CMD_RECEIVED" -> runOnUiThread {
                        action_Find_CMD_RECEIVED()
                    }
                    "Bluetooth.Take_photo_CMD_RECEIVED" -> action_Take_photo_CMD_RECEIVED()
                    "Bluetooth.Album_CMD_RECEIVED" -> action_Album_CMD_RECEIVED()
                    "Bluetooth.Search_finish_CMD_RECEIVED" -> action_search_finished_RECEIVED()
                }
            }
        }
        IntentFilter().apply {
            addAction("Bluetooth.Find_CMD_RECEIVED")
            addAction("Bluetooth.Take_photo_CMD_RECEIVED")
            addAction("Bluetooth.Album_CMD_RECEIVED")
            addAction("Bluetooth.Search_finish_CMD_RECEIVED")
            registerReceiver(commandReceiver, this)
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        model.close()
        bitmap.recycle()
        cameraDevice.close()
        unregisterReceiver(commandReceiver)
        stopService(BluetoothConnectionServiceIntent)
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
                camera.close()
            }  // todo

            override fun onError(camera: CameraDevice, error: Int) {
                camera.close()
            }  // todo
        },handler)

    }

    // Functions for different broadcasts
    private fun action_Find_CMD_RECEIVED() {
        Toast.makeText(this@MainActivity,"Find received!",Toast.LENGTH_LONG).show()
        clicked = true
    }
    private fun action_Take_photo_CMD_RECEIVED() {
        Toast.makeText(this@MainActivity,"Take photo received!",Toast.LENGTH_LONG).show()
    }

    private fun action_Album_CMD_RECEIVED() {
        Toast.makeText(this@MainActivity,"Album received!",Toast.LENGTH_LONG).show()
    }

    private fun action_search_finished_RECEIVED() {
        clicked = false
        Toast.makeText(this@MainActivity,"Search finished!",Toast.LENGTH_LONG).show()
    }

    fun get_Detection(itemName: String){  // function that get the prediction form the camera
        try {
            bitmap = textureView.bitmap!!  // get the bitmap for every frame
            var image = TensorImage.fromBitmap(bitmap) // load the bitmap using tensoeflow
            image = imageProcessor.process(image)  // pre-process the picture
            // the processed value
            val outputs = model.process(image)
            // different parameters from the processed value
            val location = outputs.locationAsTensorBuffer.floatArray
            val category = outputs.categoryAsTensorBuffer.floatArray
            val score = outputs.scoreAsTensorBuffer.floatArray
            // ready to draw out the result
            var mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(mutable)  // define the canvas to draw the result
            // define the paint size/text and location
            val h = mutable.height
            val w = mutable.width
            paint.textSize = h/15f
            paint.strokeWidth = h/85f
            var x = 0
            score.forEachIndexed { index, fl ->
                x = index
                x *= 4
                if(fl > detection_threashhold && itemName == labels.get(category.get(index).toInt())){
                    detectedTimes += 1
                    if(detectedTimes >= detectedTimes_threashhold){
                        paint.setColor(color.get(index))
                        paint.style = Paint.Style.STROKE
                        canvas.drawRect(RectF(location.get(x+1)*w, location.get(x)*h, location.get(x+3)*w, location.get(x+2)*h),paint)
                        paint.style = Paint.Style.FILL
                        canvas.drawText(itemName + " " + fl.toString(), location.get(x+1)*w, location.get(x)*h, paint)
                        paint.setColor(color.get(index))
                        paint.style = Paint.Style.STROKE
                        canvas.drawRect(RectF(location.get(x+1)*w, location.get(x)*h, location.get(x+3)*w, location.get(x+2)*h),paint)
                        paint.style = Paint.Style.FILL
                        canvas.drawText(itemName + " " + fl.toString(), location.get(x+1)*w, location.get(x)*h, paint)
                        val centerX = ((location.get(x+1)*w) + (location.get(x+3)*w)) / 2  // centerX
                        val centerY = ((location.get(x)*h) + (location.get(x+2)*h)) / 2  // centerY
                        detectedTimes = 0
                        BluetoothHelper.sendBluetoothCommand("X $${centerX.toInt()} !")
                        BluetoothHelper.sendBluetoothCommand("Y $${centerY.toInt()} !")
                    }
                }
            }
            imageView.setImageBitmap(mutable)
        } catch (e: Exception) {
            Log.e("PredictionActivity", "Model Error", e)
        }
    }


}