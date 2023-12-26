package com.example.gp_intelligent_camera_assistant

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
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
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import android.widget.Button
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.example.gp_intelligent_camera_assistant.ml.LiteModelSsdMobilenetV11Metadata2
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp

@Suppress("DEPRECATION")
class PredictionActivity : AppCompatActivity() {

    var color = listOf<Int>(
        Color.BLUE, Color.GREEN, Color.RED, Color.CYAN, Color.GRAY, Color.BLACK,
        Color.DKGRAY, Color.MAGENTA, Color.YELLOW)
    val paint = Paint()
    lateinit var labels:List<String>
    lateinit var cameraDevice: CameraDevice
    lateinit var imageProcessor: ImageProcessor
    lateinit var bitmap: Bitmap
    lateinit var imageView: ImageView
    lateinit var handler: Handler
    lateinit var cameraManager: CameraManager
    lateinit var textureView: TextureView
    lateinit var model:LiteModelSsdMobilenetV11Metadata2
    lateinit var searchedItem: String  // value that store the requested item that need to be detected

    val threashhold: Float = 0.5f

    @SuppressLint("ServiceCast", "MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        searchedItem = "bottle"  // request the item that need to be detected
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_prediction)
        labels = FileUtil.loadLabels(this, "labels.txt")
        imageProcessor = ImageProcessor.Builder().add(ResizeOp(300,300,ResizeOp.ResizeMethod.BILINEAR)).build()  //define the pre-process method
        model = LiteModelSsdMobilenetV11Metadata2.newInstance(this)  // define the model used
        var handlerThread = HandlerThread("videoThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)
        imageView = findViewById(R.id.imageViewPrediction)  // image view for displaying the detection result
        val backButton: Button = findViewById(R.id.backButton1)
        textureView = findViewById(R.id.textureViewPrediction)  // texture view for displaying the camera preview
        backButton.setOnClickListener {
            val intent = Intent(this@PredictionActivity, MainActivity::class.java)
            startActivity(intent)
        }  //Back buttons to return to the main activity
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
                    get_Detection(searchedItem) // call the prediction function
            }


        }
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        // Code block that do the automatic jump between activities
//        Handler(Looper.getMainLooper()).postDelayed({
//            val intent = Intent(this@PredictionActivity, MainActivity::class.java)
//            startActivity(intent)
//            finish()
//        }, 10000) // jump back to MainActivity after 10 seconds
    }

    fun get_Detection(itemName: String){  // function that get the prediction form the camera
        bitmap = textureView.bitmap!!  // get the bitmap for every frame
        var image = TensorImage.fromBitmap(bitmap) // load the bitmap using tensoeflow
        image = imageProcessor.process(image)  // pre-process the picture

        val outputs = model.process(image)

        val location = outputs.locationAsTensorBuffer.floatArray
        val category = outputs.categoryAsTensorBuffer.floatArray
        val score = outputs.scoreAsTensorBuffer.floatArray

        var mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutable)  // define the canvas to draw the result

        val h = mutable.height
        val w = mutable.width
        paint.textSize = h/15f
        paint.strokeWidth = h/85f
        var x = 0
        score.forEachIndexed { index, fl ->
            x = index
            x *= 4
            if(fl > threashhold && itemName == labels.get(category.get(index).toInt())){
                paint.setColor(color.get(index))
                paint.style = Paint.Style.STROKE
                canvas.drawRect(RectF(location.get(x+1)*w, location.get(x)*h, location.get(x+3)*w, location.get(x+2)*h),paint)
                paint.style = Paint.Style.FILL
                canvas.drawText(itemName + " " + fl.toString(), location.get(x+1)*w, location.get(x)*h, paint)
            }
        }
        imageView.setImageBitmap(mutable)
    }

    override fun onDestroy() {
        super.onDestroy()
        model.close()
    }

    override fun onPause() {
        super.onPause()
        cameraDevice?.close()
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