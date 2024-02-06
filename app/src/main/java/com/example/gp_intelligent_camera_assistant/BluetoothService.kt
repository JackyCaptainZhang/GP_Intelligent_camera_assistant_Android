package com.example.gp_intelligent_camera_assistant

import android.app.Service
import android.app.Service.START_NOT_STICKY
import android.content.Intent
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import java.io.IOException

class BluetoothService : Service(){
    private lateinit var bluetoothThread: Thread

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        bluetoothThread = Thread(Runnable {
            try {
                val stringBuilder = StringBuilder() // 用于存储接收到的数据

                while (true) {
                    val buffer = ByteArray(1024) // 缓冲区大小可以根据需要调整
                    val bytesRead = BluetoothHelper.inputStream.read(buffer)
                    if (bytesRead > 0) {
                        val receivedData = String(buffer, 0, bytesRead)
                        stringBuilder.append(receivedData) // 将接收到的数据添加到字符串构建器

                        // 检查是否包含换行符 "\n"
                        val data = stringBuilder.toString()
                        if (data.contains("\n")) {
                            val parts = data.split("\n") // 使用换行符拆分数据
                            for (part in parts) {
                                Log.d("Receive", part) // 输出拆分后的部分
                            }
                            // 清空字符串构建器，以准备接收下一个消息
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

    override fun onDestroy() {
        super.onDestroy()
        bluetoothThread.interrupt() // 确保在服务停止时中断线程
    }
}