package tech.picnic.fingerpaintview

import android.graphics.Path

//Create an extension function for the Path class called addHeart
fun Path.addHeart(x: Float, y: Float, width: Float, height: Float) {
    moveTo(x, y + height / 4)
    cubicTo(x, y, x - width / 2, y - height / 2, x, y - height / 4)
    cubicTo(x, y - height / 2, x + width / 2, y, x, y + height / 4)
    close()
}

//Create an extension function for Path that accepts, x, y, width, height, and number of sides as parameters
fun Path.addPolygon(x: Float, y: Float, width: Float, height: Float, sides: Int) {
    val angle = 2.0 * Math.PI / sides
    moveTo(
        (x + width * Math.cos(0.0)).toFloat(),
        (y + height * Math.sin(0.0)).toFloat()
    )
    for (i in 1 until sides) {
        lineTo(
            (x + width * Math.cos(angle * i)).toFloat(),
            (y + height * Math.sin(angle * i)).toFloat()
        )
    }
    close()
}
