package com.example.gp_intelligent_camera_assistant

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.media.MediaScannerConnection
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.gp_intelligent_camera_assistant.ml.LiteModelSsdMobilenetV11Metadata2
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


class MainActivity : AppCompatActivity() {
    // System inner parameters
    lateinit var cameraDevice: CameraDevice
    lateinit var handler: Handler
    lateinit var cameraManager: CameraManager
    lateinit var cameraCaptureSession: CameraCaptureSession
    lateinit var captureRequest: CaptureRequest.Builder
    lateinit var imageReader: ImageReader
    lateinit var textureView: TextureView
    lateinit var textViewOverlay: TextView
    private lateinit var voiceRecognizer: VoiceRecognizer
    private lateinit var commandReceiver: BroadcastReceiver
    private lateinit var BluetoothConnectionServiceIntent: Intent
    lateinit var bitmap: Bitmap
    lateinit var model: LiteModelSsdMobilenetV11Metadata2
    lateinit var labels:List<String>
    lateinit var imageProcessor: ImageProcessor
    var color =
        listOf<Int>(
        Color.BLUE, Color.GREEN, Color.RED,
        Color.CYAN, Color.GRAY, Color.BLACK,
        Color.DKGRAY, Color.MAGENTA, Color.YELLOW) // painting colors
    val paint = Paint()
    lateinit var imageView: ImageView
    var detectedTimes: Int = 0

    // system adjustable parameters
    var detectedTimes_threashhold = 10 // control the detection FPS
    var clicked: Boolean = false // control the turn on/off of the detection function
    var itemTOSearchReceived: Boolean = false
    var speechRecognitionActivated: Boolean = false
    val detection_threashhold: Float = 0.5f // control the detection sensibility
    var itemTOSearch: String = ""


    @SuppressLint("ServiceCast", "MissingPermission", "UnspecifiedRegisterReceiverFlag",
        "MissingInflatedId"
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        get_permissions()  //ask for required permission at the launch of the App
        // Camera definitions
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        // label and model definition
        labels = FileUtil.loadLabels(this, "labels.txt")
        imageProcessor = ImageProcessor.Builder().add(ResizeOp(300,300,ResizeOp.ResizeMethod.BILINEAR)).build()  //define the pre-process method
        model = LiteModelSsdMobilenetV11Metadata2.newInstance(this)  // define and initialize the model
        // handler definition
        var handlerThread = HandlerThread("videoThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)
        // UI element definition
        textViewOverlay = findViewById(R.id.textViewOverlay)
        textureView = findViewById(R.id.textureView)  // texture view for displaying the camera preview
        imageView = findViewById(R.id.PredictionimageView)  // image view for displaying the detection result
        imageView.visibility = View.GONE
        // Image reader for capturing photos
        imageReader = ImageReader.newInstance(1920,1080,ImageFormat.JPEG,1)
        // This defines the saving mechanism by using latest media store api
        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader?.acquireNextImage()
            image?.let {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "img_${System.currentTimeMillis()}.jpeg") // File name
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg") // File type
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/Camera") // File path

                }

                val contentResolver = contentResolver
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

                try {
                    val outputStream = contentResolver.openOutputStream(uri!!)
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    outputStream?.write(bytes)
                    outputStream?.close()
                    image.close()
                    Toast.makeText(this@MainActivity, "Image saved", Toast.LENGTH_SHORT).show()
                } catch (e: IOException) {
                    Log.e("MainActivity", "Error saving image", e)
                }
            }
        }, handler)
        //Buttons definition
        val getPredictionButton: Button = findViewById(R.id.getPredictionButton)
        val takephotoButton: Button = findViewById(R.id.takephotoButton)
        val albumButton: Button = findViewById(R.id.albumButton)

        // prediction button action for test purpose
        getPredictionButton.setOnClickListener{
            clicked = true
        }

        takephotoButton.setOnClickListener {
            takePhoto()
        }

        albumButton.setOnClickListener {
            openGallery()
        }



        // Initialise the speech recognition
        voiceRecognizer = VoiceRecognizer(this)

        textureView.surfaceTextureListener = object:TextureView.SurfaceTextureListener{
            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                surface.setDefaultBufferSize(1920, 1080)
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

            @SuppressLint("SetTextI18n")
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) { // Control the behaviour of the detection view and function
                if (clicked){ // Show the detection view
                    if(!itemTOSearchReceived && !speechRecognitionActivated){
                        promptSpeechInput()
                    }
                    if(itemTOSearchReceived){ // search layout
                        imageView.visibility = View.VISIBLE
                        takephotoButton.visibility = View.INVISIBLE
                        albumButton.visibility = View.INVISIBLE
                        getPredictionButton.visibility = View.INVISIBLE
                        textViewOverlay.text = "You are now finding: $itemTOSearch"
                        get_Detection(itemTOSearch)
                    }
                }else{ // Main layout
                    itemTOSearchReceived = false
                    speechRecognitionActivated = false
                    imageView.visibility = View.GONE
                    takephotoButton.visibility = View.VISIBLE
                    albumButton.visibility = View.VISIBLE
                    getPredictionButton.visibility = View.VISIBLE
                }
            }
        }

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

    @SuppressLint("InlinedApi")
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
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED){
            permissionList.add(android.Manifest.permission.RECORD_AUDIO)
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
                captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)  //define a capture request from camera device
                captureRequest.addTarget(surface)  // attach capture request to the surface

                cameraDevice.createCaptureSession(listOf(surface, imageReader.surface), object: CameraCaptureSession.StateCallback(){
                    override fun onConfigured(session: CameraCaptureSession) {
                        cameraCaptureSession = session
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

    fun openGallery() { // Open the album
        var context: Context = this@MainActivity
        val intent = Intent(Intent.ACTION_VIEW).apply {
            type = "image/*"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Not find album!", Toast.LENGTH_SHORT).show()
        }
    }

    fun takePhoto(){ // Take photo. it will call the saving mechanism defined in 'imageReader' above
        captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
        captureRequest.addTarget(imageReader.surface)
        cameraCaptureSession.capture(captureRequest.build(),null,null)
    }

    // Actions when receiving different broadcasts
    private fun action_Find_CMD_RECEIVED() {
        Toast.makeText(this@MainActivity,"Find received!",Toast.LENGTH_SHORT).show()
        clicked = true
    }
    private fun action_Take_photo_CMD_RECEIVED() {
        takePhoto()
        Toast.makeText(this@MainActivity,"Take photo received!",Toast.LENGTH_SHORT).show()
    }

    private fun action_Album_CMD_RECEIVED() {
        openGallery()
        Toast.makeText(this@MainActivity,"Album received!",Toast.LENGTH_SHORT).show()
    }

    private fun action_search_finished_RECEIVED() {
        clicked = false
        Toast.makeText(this@MainActivity,"Search finished!",Toast.LENGTH_SHORT).show()
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
                        BluetoothInitialiser.sendBluetoothCommand("X $${centerX.toInt()} !")
                        BluetoothInitialiser.sendBluetoothCommand("Y $${centerY.toInt()} !")
                    }
                }
            }
            imageView.setImageBitmap(mutable)
        } catch (e: Exception) {
            Log.e("PredictionActivity", "Model Error", e)
        }
    }

    // Function for calling google speech recognition API
    fun promptSpeechInput() {
        speechRecognitionActivated = true
        voiceRecognizer.askSpeechInput()
    }

    // Function that get the speech recognition result
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VoiceRecognizer.REQUEST_CODE_SPEECH_INPUT && resultCode == Activity.RESULT_OK && data != null) {
            val result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            itemTOSearch = result?.joinToString().toString().lowercase() // Write the item to search to the global class
            itemTOSearchReceived = true
        }
    }
}