package tech.picnic.fingerpaintview

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View

fun View.getBitmapFromView() : Bitmap {
    val bitmap = Bitmap.createBitmap(
        width, height, Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bitmap)
    draw(canvas)
    return bitmap
}


fun View.getBitmapFromView(defaultColor: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(
        width, height, Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bitmap)
    canvas.drawColor(defaultColor)
    draw(canvas)
    return bitmap
}