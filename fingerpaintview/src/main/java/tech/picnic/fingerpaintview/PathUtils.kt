package tech.picnic.fingerpaintview

import android.graphics.Path
import android.graphics.RectF
import tech.picnic.fingerpaintview.MathUtils.Companion.distance
import tech.picnic.fingerpaintview.MathUtils.Companion.slope
import tech.picnic.fingerpaintview.MathUtils.Companion.yIntercept

class PathUtils private constructor() {
    init {
        throw AssertionError("No instances allowed")
    }

    companion object {
        /**
         * Creates a regular convex polygon [android.graphics.Path].
         *
         * @param left            Left bound
         * @param top             Top bound
         * @param right           Right bound
         * @param bottom          Bottom bound
         * @param numSides        Number of sides
         * @param rotationDegrees Degrees to rotate polygon
         * @return A [android.graphics.Path] corresponding to a regular convex polygon.
         */
        fun regularConvexPolygon(
            left: Float, top: Float, right: Float, bottom: Float,
            numSides: Int, rotationDegrees: Float
        ): Path {
//            if (right - left != bottom - top) {
//                throw IllegalArgumentException(
//                    "Provided bounds (" + left + ", " + top + ", " +
//                            right + ", " + bottom + ") must be square."
//                )
//            }
            if (numSides < 3) {
                throw IllegalArgumentException("Number of sides must be at least 3.")
            }
            val radius = (right - left) / 2f
            val degreesBetweenPoints = 360f / numSides

            // Add 90 so first point is top
            val baseRotation = 90 + rotationDegrees

            // Assume we want a point of the polygon at the top unless otherwise set
            val startDegrees =
                if (numSides % 2 != 0) baseRotation else baseRotation + degreesBetweenPoints / 2f
            val path = Path()
            for (i in 0..numSides) {
                val theta = Math.toRadians((startDegrees + i * degreesBetweenPoints).toDouble())
                val x = (left + radius + (radius * Math.cos(theta))).toFloat()
                val y = (top + radius - (radius * Math.sin(theta))).toFloat()
                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }
            return path
        }

        /**
         * Creates a regular star polygon [android.graphics.Path].
         *
         * @param left            Left bound
         * @param top             Top bound
         * @param right           Right bound
         * @param bottom          Bottom bound
         * @param numPoints       Number of points on star
         * @param density         Density of the star polygon (the number of vertices, or points, to
         * skip when drawing a line connecting two vertices.)
         * @param rotationDegrees Number of degrees to rotate star polygon
         * @param outline         True if only the star's outline should be drawn. If false, complete
         * lines will be drawn connecting the star's vertices.
         * @return A [android.graphics.Path] corresponding to a regular star polygon.
         */
        fun regularStarPolygon(
            left: Int, top: Int, right: Int, bottom: Int,
            numPoints: Int, density: Int, rotationDegrees: Float,
            outline: Boolean
        ): Path {
            var numPoints = numPoints
            if (right - left != bottom - top) {
                throw IllegalArgumentException(
                    ("Provided bounds (" + left + ", " + top + ", " +
                            right + ", " + bottom + ") must be square.")
                )
            }
            if (numPoints < 5) {
                throw IllegalArgumentException("Number of points must be at least 5")
            }
            if (density < 2) {
                throw IllegalArgumentException("Density must be at least 2")
            }
            val outerRadius = (right - left) / 2f

            // Add 90 so first point is top
            val startDegrees = 90 + rotationDegrees
            var degreesBetweenPoints = 360f / numPoints
            val outerPointsArray = Array(numPoints) {
                FloatArray(
                    2
                )
            }
            for (i in 0 until numPoints) {
                val theta =
                    Math.toRadians((startDegrees + density * i * degreesBetweenPoints).toDouble())
                outerPointsArray[i][0] = (outerRadius + (outerRadius * Math.cos(theta))).toFloat()
                outerPointsArray[i][1] = (outerRadius - (outerRadius * Math.sin(theta))).toFloat()
            }
            val pointsForPath: Array<FloatArray>
            if (outline) {
                // Find the first intersection point created by drawing each line in the star
                var firstIntersection: FloatArray? = null
                val firstPt1 = outerPointsArray[0]
                val firstPt2 = outerPointsArray[1]
                val firstSlope: Float = slope(firstPt1, firstPt2)
                val firstYInt: Float = yIntercept(firstPt1, firstSlope)

                // Ranges for first line. We'll use these later to check if the intersection we find
                // is in the valid range.
                val firstLowX = Math.min(firstPt1[0], firstPt2[0])
                val firstHighX = Math.max(firstPt1[0], firstPt2[0])
                val firstLowY = Math.min(firstPt1[0], firstPt2[1])
                val firstHighY = Math.max(firstPt1[1], firstPt2[1])

                // The second line and the last line can't intersect the first line. Skip them.
                for (i in 2 until (outerPointsArray.size - 1)) {
                    val curPt1 = outerPointsArray[i]
                    val curPt2 = outerPointsArray[i + 1]
                    val curSlope: Float = slope(curPt1, curPt2)
                    val curYInt = curPt1[1] - curSlope * curPt1[0]

                    // System of equations. Two equations, two unknowns.
                    // y = firstSlope * x + firstYInt
                    // y = curSlope * x + curYInt

                    // Solve for x and y in terms of known quantities.
                    // firstSlope * x + firstYInt = curSlope * x + curYInt
                    // firstSlope * x - curSlope * x = curYInt - firstYInt
                    // x * (firstSlope - curSlope) = (curYInt - firstYInt)
                    // x = (curYInt - firstYInt) / (firstSlope - curSlope)
                    // y = firstSlope * x + firstYInt
                    if (firstSlope == curSlope) {
                        // lines can't intersect if they are parallel
                        continue
                    }
                    val intersectionX = (curYInt - firstYInt) / (firstSlope - curSlope)
                    val intersectionY = firstSlope * intersectionX + firstYInt

                    // Ranges for current line.
                    val curLowX = Math.min(curPt1[0], curPt2[0])
                    val curHighX = Math.max(curPt1[0], curPt2[0])
                    val curLowY = Math.min(curPt1[0], curPt2[1])
                    val curHighY = Math.max(curPt1[1], curPt2[1])

                    // Range where intersection has to be.
                    val startX = Math.max(firstLowX, curLowX)
                    val endX = Math.min(firstHighX, curHighX)
                    val startY = Math.max(firstLowY, curLowY)
                    val endY = Math.min(firstHighY, curHighY)
                    if ((intersectionX > startX) && (intersectionX < endX) && (
                                intersectionY > startY) && (intersectionY < endY)
                    ) {
                        // Found intersection.
                        firstIntersection = floatArrayOf(intersectionX, intersectionY)
                    }
                }
                if (firstIntersection == null) {
                    // If there are no intersections, it's not a star polygon.
                    throw IllegalStateException(
                        "Failed to calculate path." +
                                "Are the number of points and density valid?"
                    )
                }

                // Use the first intersection point to find the radius of the inner circle of the star
                val innerRadius: Float = distance(
                    outerRadius, outerRadius,
                    firstIntersection[0],
                    firstIntersection[1]
                )

                // There are now twice as many points to "line to" in our path
                numPoints *= 2

                // Recalculate degrees between points
                degreesBetweenPoints = 360f / numPoints
                pointsForPath = Array(numPoints) { FloatArray(2) }
                for (i in 0 until numPoints) {
                    val theta = Math.toRadians((startDegrees + i * degreesBetweenPoints).toDouble())
                    val radius = if (i % 2 == 0) outerRadius else innerRadius
                    pointsForPath[i][0] = (outerRadius + (radius * Math.cos(theta))).toFloat()
                    pointsForPath[i][1] = (outerRadius - (radius * Math.sin(theta))).toFloat()
                }
            } else {
                pointsForPath = outerPointsArray
            }

            // Make the Path from whatever points array we're using -- outline or not
            val path = Path()
            for (i in pointsForPath.indices) {
                val x = left + pointsForPath[i][0]
                val y = top + pointsForPath[i][1]
                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }
            path.lineTo(left + pointsForPath[0][0], top + pointsForPath[0][1])
            return path
        }

        /**
         * Creates a circle [android.graphics.Path].
         *
         * @param left   Left bound
         * @param top    Top bound
         * @param right  Right bound
         * @param bottom Bottom bound
         * @return A [android.graphics.Path] corresponding to a circle.
         */
        fun circle(left: Float, top: Float, right: Float, bottom: Float): Path {
            if (right - left != bottom - top) {
                throw IllegalArgumentException(
                    ("Provided bounds (" + left + ", " + top + ", " +
                            right + ", " + bottom + ") must be square.")
                )
            }
            val path = Path()
            // sweep angle is mod 360, so we can't actually use 360.
            path.arcTo(
                RectF(left, top, right, bottom),
                0f,
                359.9999f
            )
            return path
        }
    }
}