## Project Introduction

The project invovles in **Arduino-based gimbal develop** and **Android App develop**. This Project is an accessitive technology for those who has disabilities that can not hold the phone to take the photo. So the final aim is to enable them to just use their voice to finish photography composition and photo taking. In the final product, user will only need to say command like "Find birds". And the gimble will automatically move to search for the birds in the view. The gimbal will keep the bird in the center of the screen if the bird is detected. And user can say "take photo" to caputer the photo.

Also, we have the basic precise movement control. User can say like "Move Left" and the gimbal will rotate to the left until user say "Stop". User can adjust the gimbal to any direction very easily and smoothly. Unlike the advanced search function mentioned above , these basic movement control can work without the phone, which means any other devices (like USB cameras) who does not have bluetooth function can also be attached to this device and user can control its movement very easily.



# Software 

## 1. The basic object detection function

2023-11-24

To relize the advanced search function mentioned in introduction part, we must make sure that there is a way to make Android phone recognise different items in camera view. And TenserFlow has a lite version that can be applied to the phone and it is open-sourced. What is more, based on the open-sourced COCO dataset by Facebook and Microsoft, TenserFlow has a pre-trained model to carry out the object detection. Here are the steps:

1. Set up the camera and storage permission in Manifest

   ```XML
   <uses-permission-sdk-23 android:name="android.permission.CAMERA"/>
       <uses-permission-sdk-23 android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>
       <uses-permission-sdk-23 android:name="android.permission.READ_EXTERNAL_STORAGE"/>
   ```

2. Preview the camera (textureView, surface)

   ```xml
   <TextureView
           android:layout_width="match_parent"
           android:layout_height="match_parent"
           android:id="@+id/textureView"/>
   ```

   ```kotlin
   override fun onCreate(savedInstanceState: Bundle?) {
           super.onCreate(savedInstanceState)
           setContentView(R.layout.activity_main)
           get_permissions()  //ask for required permission at the launch of the App
           var handlerThread = HandlerThread("videoThread")
           handlerThread.start()
           handler = Handler(handlerThread.looper)
           textureView = findViewById(R.id.textureView)
           textureView.surfaceTextureListener = object:TextureView.SurfaceTextureListener{
               override fun onSurfaceTextureAvailable(
                   surface: SurfaceTexture,
                   width: Int,
                   height: Int
               ) {
                   surface.setDefaultBufferSize(3120, 4160)
                   openCamera()  //call methods to open the camera
               }
   ```

   ```kotlin
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
   ```

3. Import the pre-trained Tensor Flow Model (based on COCO dataset)

   ```kotlin
    model = LiteModelSsdMobilenetV11Metadata2.newInstance(this)  // define the model used
    labels = FileUtil.loadLabels(this, "labels.txt")
   ```

4. Pre-process the image (resize 300x300)

   ```kotlin
    imageProcessor = ImageProcessor.Builder().add(ResizeOp(300,300,ResizeOp.ResizeMethod.BILINEAR)).build()  //define the pre-process method
   ```

5. Call Tensor Flow process

6. Attach and display the result on the canvas (imageView)

   ```xml
   <ImageView
           android:layout_width="match_parent"
           android:layout_height="match_parent"
           android:background="#000"
           android:id="@+id/imageView"/>
   ```

   ```kotlin
   imageView = findViewById(R.id.imageView)
   ```

```kotlin
fun get_Detection(){  // function that get the prediction form the camera
        bitmap = textureView.bitmap!!  // get the bitmap for every frame
        var image = TensorImage.fromBitmap(bitmap) // load the bitmap using tensoeflow
        image = imageProcessor.process(image)  // pre-process the picture

        val outputs = model.process(image)  // process the image

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
            if(fl > threashhold) {
                Log.i("MyAppTag",labels.get(category.get(index).toInt()))
            }
                paint.setColor(color.get(index))
                paint.style = Paint.Style.STROKE
                canvas.drawRect(RectF(location.get(x+1)*w, location.get(x)*h, location.get(x+3)*w, location.get(x+2)*h),paint)
                paint.style = Paint.Style.FILL
                canvas.drawText(labels.get(category.get(index).toInt()) + " " + fl.toString(), location.get(x+1)*w, location.get(x)*h, paint)
        }
            imageView.setImageBitmap(mutable)
    }
```



We have realised the basic object detection. Next step we want to separate the detection function by using different views. Or in other words, the detection function can be turned on/off at user's will.



## 2. Switch between different activity views

2023-12-26

In Android Studio, to switch between different views, we use **Intent**. And we can trigger the Intent by using buttons.

```xml
<Button
            android:id="@+id/getPredictionButton"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="Object Detection"
            android:layout_alignParentStart="true"
            android:layout_alignParentBottom="true"
            android:layout_marginBottom="50dp"
            android:layout_marginStart="2dp" /> 
// Define the button in UI
```

```Kotlin
val getPredictionButton: Button = findViewById(R.id.getPredictionButton) 
// Find the button
getPredictionButton.setOnClickListener{
        val intent = Intent(this@MainActivity, PredictionActivity::class.java)
        startActivity(intent)
   } // Monitor the button and trigger the Intent
```

Where `this@MainActivity` is the current activity and `PredictionActivity::class.java` is the destination activity.

What is more, apart from using buttons, Intent can also be triggered by the timer. In Android Studio, it is the `lopper`

```kotlin
Handler(Looper.getMainLooper()).postDelayed({
      val intent = Intent(this@PredictionActivity, MainActivity::class.java)
      startActivity(intent)
      finish()
  }, 10000) // jump back to MainActivity after 10 seconds
```

This code will jump from the PredictionActivity to MainActivity automatically after 10 seconds. We can do the automatic jump between activities by using lopper.

Next we will go to the bluetooth part.



## 3. Bluetooth Communication part one: Setup and send the object position to the Arduino

2024-01-10

This stage is for realizing the bidirectional bluetooth communication between App and HC-05. Here are the steps:

1. Get the bluetooth permission
2. Define the UUID, bluetooth manager.
3. Use bluetooth manager to find the bluetooth aapter hardware and find the bonded device
4. Find the bonded HC-05 module by unique MAC address
5. define the device to the socket and connect to the socket
6. define the input stream and output stream for sending and receiving

We better seperate the Bluetooth functions from the activities (`BluetoothHelper.Object`). So we can make the bluetooth functions isolate from the switching between different activities.

```kotlin
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

        } catch (e: Exception){
        }
    }
    fun sendBluetoothCommand(sentCMD: String){
        val dataToSend = sentCMD + "\n" // Must have a "\n" at the end of the message
        val bytes = dataToSend.toByteArray() // change the message to the Byte for serial transmission
        outputStream.write(bytes) // write the message to the output stream
    }
}
```

We can now setup the Bluetooth class and connect to bluetooth in `MainActivity`.

```kotlin
try {
            BluetoothHelper.init(this)
            BluetoothHelper.connectTOBluetooth()
            connected = true
            BluetoothHelper.sendBluetoothCommand("Hello!!!")
        }catch (e: Exception){
        }
```

Also, as long as bluetooth is connected, we can directly call the sending function in other activities (like in `PredictionActivity`)

```kotlin
BluetoothHelper.sendBluetoothCommand("X $centerX !")
BluetoothHelper.sendBluetoothCommand("Y $centerY !")
```



So now the bluetooth function is isolated from any activity class and can be used at our will. Next we will go to bluetooth receiving part.



## 4. Bluetooth Communication part two: Receive the command from the Arduino

Unlike sending commands, receiving commands need consistent monitor of the input stream, which means we need to apply the multithread to separate it from the main thread in avoidance of any thread jamming. To do this, we need to set up a service class `BluetoothService` to monitor the bluetooth input.

```kotlin
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
                                // add Broadcast here
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
```

We can just use following code to start the thread in the `Mainactivity` :

```kotlin
val bluetoothServiceIntent = Intent(this, BluetoothService::class.java)
startService(bluetoothServiceIntent)
```



Now we can receive the command from the Arduino, and next is to use the **Broadcast** to trigger the corresponding actions for different received commands.



## 5. Turn on the needed functions by different received CMDs

In `BluetoothService` , we use **Broadcast** to monitor different commands and trigger the different actions in `Mainactivity`

```kotlin
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
```

In `Mainactivity` , we use following code in `onCreate` method to register, monitor and trigger different actions:

```kotlin
// register, monitor the broadcast and trigger the functions
        commandReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    "Bluetooth.Find_CMD_RECEIVED" -> action_Find_CMD_RECEIVED()
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
```

```kotlin
private fun action_Find_CMD_RECEIVED() {
        Toast.makeText(this@MainActivity,"Find received!",Toast.LENGTH_LONG).show()
    }
    private fun action_Take_photo_CMD_RECEIVED() {
        Toast.makeText(this@MainActivity,"Take photo received!",Toast.LENGTH_LONG).show()
    }

    private fun action_Album_CMD_RECEIVED() {
        Toast.makeText(this@MainActivity,"Album received!",Toast.LENGTH_LONG).show()
    }
```



Next we want to trigger the Android speech recognition function after we say "Find" to specify the item we would like to search for.

## 6. Android speech recognition API for Find function

Google speech recognition API is built in Android and can be easy called. To call it, we need to have a new class `VoiceRecognizer`. In which we use `Intent` to define the calling function.

```kotlin
class VoiceRecognizer(private val activity: Activity) {
    companion object {
        const val REQUEST_CODE_SPEECH_INPUT = 1000
    }

    fun askSpeechInput() {
        if (ContextCompat.checkSelfPermission(activity, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, arrayOf(android.Manifest.permission.RECORD_AUDIO), REQUEST_CODE_SPEECH_INPUT)
        } else {
            startVoiceRecognitionActivity()
        }
    }

    private fun startVoiceRecognitionActivity() {
        if (!android.speech.SpeechRecognizer.isRecognitionAvailable(activity)) {
            Toast.makeText(activity, "Speech recognition is not available on this device.", Toast.LENGTH_SHORT).show()
        } else {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Say the item you want to find: ")
            }
            activity.startActivityForResult(intent, REQUEST_CODE_SPEECH_INPUT)
        }
    }
}
```

To call it in `MainActivity`, we need to initialise the object

```kotlin
private lateinit var voiceRecognizer: VoiceRecognizer

voiceRecognizer = VoiceRecognizer(this)
```

And we can call the API like this:

```kotlin
var itemTOSearch: String = ""
var itemTOSearchReceived: Boolean = false

fun promptSpeechInput() {
        speechRecognitionActivated = true
        voiceRecognizer.askSpeechInput()
    }

    // Function that get the speech recognition result
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VoiceRecognizer.REQUEST_CODE_SPEECH_INPUT && resultCode == Activity.RESULT_OK && data != null) {
            val result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            itemTOSearch = result?.joinToString().toString() // Write the item to search to the global class
            itemTOSearchReceived = true
        }
    }
```

Now we need to implement the calling logic. When user say "Find" command, before triggering the detection function, user should say the item that need to be found by system. To do this, we can have codes like this:

```kotlin
override fun onSurfaceTextureUpdated(surface: SurfaceTexture) { // Control the behaviour of the detection view and function
                if (clicked){ // Show the detection view
                    if(!itemTOSearchReceived && !speechRecognitionActivated){
                        promptSpeechInput()
                    }
                    if(itemTOSearchReceived){
                        bluetoothConnectButton.visibility = View.INVISIBLE
                        getPredictionButton.visibility = View.INVISIBLE
                        imageView.visibility = View.VISIBLE
                        textViewOverlay.text = "You are now finding: $itemTOSearch"
                        get_Detection(itemTOSearch)
                    }
                }else{
                    itemTOSearchReceived = false
                    speechRecognitionActivated = false
                    imageView.visibility = View.GONE // Hide the detection view
                    bluetoothConnectButton.visibility = View.VISIBLE
                    getPredictionButton.visibility = View.VISIBLE
                }
            }
```



## 7. Last few steps

As I mentioned above, this App will not have the complex UI as system camera is. But we can have basic functions like photo taking and saving to the system album. It is quite easy to implement these two functions:

```kotlin
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
```

# Hardware
See https://github.com/JackyCaptainZhang/GP_Intelligent_camera_assistant_Arduino

# Development Notes

1. Important Flags in Arduino:

* Moving

* Searching
* horizontal

2. Up and down servo:

* Initial angle: 120
* Up: 120++ to 150
* Down: 120-- to 80

3. Left and right servo:

* Initial angle: 140
* Left: 140++ to 180
* Right: 140-- to 100

4. Rotate: 90 (vertical) to 0 (horizontal)

5. Centre of Screen (360,753)

* move down if > 753
* move up if < 753
* move left if < 360
* move right if > 360

6. LED: 

```c++
pinMode(ledPin, OUTPUT);
ledState = LOW/HIGH;
digitalWrite(ledPin, ledState);
```

7. Rotate motor: Yellow (Signal), Green (Power), Grey (GND)



