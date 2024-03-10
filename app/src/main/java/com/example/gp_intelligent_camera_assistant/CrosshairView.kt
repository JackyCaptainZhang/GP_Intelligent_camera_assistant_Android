package com.example.gp_intelligent_camera_assistant

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class CrosshairView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        strokeWidth = 6f 
    }
    private var width: Int = 0
    private var height: Int = 0

    override fun onSizeChanged(newWidth: Int, newHeight: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(newWidth, newHeight, oldWidth, oldHeight)
        width = newWidth
        height = newHeight
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val centerY = height / 2f
        val crosshairLength = 30f

        canvas.drawLine(centerX - crosshairLength, centerY, centerX + crosshairLength, centerY, paint) // 水平线
        canvas.drawLine(centerX, centerY - crosshairLength, centerX, centerY + crosshairLength, paint) // 垂直线
    }
}
