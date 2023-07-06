package tech.picnic.fingerpaintview

import kotlin.math.pow
import kotlin.math.sqrt

internal class MathUtils private constructor() {
    init {
        throw AssertionError("No instances allowed")
    }

    companion object {
        fun slope(point1: FloatArray, point2: FloatArray): Float {
            return (point2[1] - point1[1]) / (point2[0] - point1[0])
        }

        fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
            return sqrt(
                (y2 - y1).toDouble().pow(2.0) + (x2 - x1).toDouble().pow(2.0)
            ).toFloat()
        }

        fun yIntercept(point: FloatArray, slope: Float): Float {
            return point[1] - slope * point[0]
        }
    }
}