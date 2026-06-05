package com.geeplayer.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

object ImageUtils {

    fun createPlaceholderCover(size: Int = 280): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(30, 30, 58))
        val paint = Paint().apply {
            color = Color.rgb(103, 80, 164)
            isAntiAlias = true
            textSize = size * 0.3f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawCircle(size / 2f, size / 2f, size * 0.35f, paint)
        paint.color = Color.WHITE
        paint.textSize = size * 0.4f
        canvas.drawText("♪", size / 2f, size * 0.6f, paint)
        return bitmap
    }
}
