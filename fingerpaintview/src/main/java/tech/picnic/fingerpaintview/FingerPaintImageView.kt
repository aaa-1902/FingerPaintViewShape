package tech.picnic.fingerpaintview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.appcompat.widget.AppCompatImageView
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import nl.picnic.fingerpaintview.R
import kotlin.math.sqrt

class FingerPaintImageView @JvmOverloads constructor(context: Context,
                                                     attrs: AttributeSet? = null,
                                                     defStyleAttr: Int = 0,
                                                     defStyleRes: Int = 0) :
        AppCompatImageView(context, attrs, defStyleAttr) {

    private enum class BrushType {
        BLUR, EMBOSS, NORMAL, ERASER, SHAPE_CIRCLE, SHAPE_SQUARE
    }

    private enum class PaintStyle {
        STROKE, FILL
    }

    private val TAG = "FingerPaintImageView"
    private val defaultStrokeColor = Color.WHITE
    private val defaultStrokeWidth = 12f
    private val defaultTouchTolerance = 4f
    private val defaultBitmapPaint = Paint(Paint.DITHER_FLAG)
    private var brushBitmap: Bitmap? = null
    private var brushCanvas: Canvas? = null
    private var countDrawn = 0
    private var currentBrush = BrushType.NORMAL
    private var currentPaintStyle = PaintStyle.STROKE

    var inEditMode = false
    var isInEraserMode = false

    private val defaultEmboss: EmbossMaskFilter by lazy {
        EmbossMaskFilter(floatArrayOf(1F, 1F, 1F), 0.4F, 6F, 3.5F)
    }
    private val defaultBlur: BlurMaskFilter by lazy {
        BlurMaskFilter(5F, BlurMaskFilter.Blur.NORMAL)
    }

    var strokeColor = defaultStrokeColor
        set(value) {
            field = value
            pathPaint.color = value
        }

    var strokeWidth = defaultStrokeWidth
        set(value) {
            field = value
            pathPaint.strokeWidth = value
        }

    private val matrixValues = FloatArray(9)
        get() = field.apply { imageMatrix.getValues(this) }

    var touchTolerance = defaultTouchTolerance

    private val pathPaint = Paint().also {
        it.isAntiAlias = true
        it.isDither = true
        it.color = strokeColor
        if(currentPaintStyle == PaintStyle.STROKE)
            it.style = Paint.Style.STROKE
        else
            it.style = Paint.Style.FILL
        it.strokeJoin = Paint.Join.ROUND
        it.strokeCap = Paint.Cap.ROUND
        it.strokeWidth = strokeWidth

        when(currentBrush){
            BrushType.ERASER -> it.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            else -> it.xfermode = null
        }
    }

    

    private var startingX = 0f;
    private var startingY = 0f;
    private var currentX = 0f
    private var currentY = 0f
    private var paths: MutableList<Pair<Path, Paint>> = mutableListOf()
    private var previewPaths: MutableList<Pair<Path, Paint>> = mutableListOf()

    init {
        if (attrs != null) {
            val typedArray = context.theme.obtainStyledAttributes(attrs,
                    R.styleable.FingerPaintImageView, defStyleAttr, defStyleRes)
            try {
                strokeColor = typedArray.getColor(R.styleable.FingerPaintImageView_strokeColor, defaultStrokeColor)
                strokeWidth = typedArray.getDimension(R.styleable.FingerPaintImageView_strokeWidth, defaultStrokeWidth)
                inEditMode = typedArray.getBoolean(R.styleable.FingerPaintImageView_inEditMode, false)
                touchTolerance = typedArray.getFloat(R.styleable.FingerPaintImageView_touchTolerance, defaultTouchTolerance)
            } finally {
                typedArray.recycle()
            }
        }
    }

    /**
     * Get current screen's width and height
     */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        brushBitmap = Bitmap.createBitmap(w,
                h,
                Bitmap.Config.ARGB_8888)
        brushCanvas = Canvas(brushBitmap)
    }

    /**
     * If there are any paths drawn on top of the image, this will return a bitmap with the original
     * content plus the drawings on top of it. Otherwise, the original bitmap will be returned.
     */
    override fun getDrawable(): Drawable? {
        return super.getDrawable()?.let {
            if (!isModified()) return it

            val inverse = Matrix().apply { imageMatrix.invert(this) }
            val scale = FloatArray(9).apply { inverse.getValues(this) }[Matrix.MSCALE_X]

            // draw original bitmap
            val result = Bitmap.createBitmap(it.intrinsicWidth, it.intrinsicHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            it.draw(canvas)

            val transformedPath = Path()
            val transformedPaint = Paint()
            paths.forEach { (path, paint) ->
                path.transform(inverse, transformedPath)
                transformedPaint.set(paint)
                transformedPaint.strokeWidth *= scale
                canvas.drawPath(transformedPath, transformedPaint)
            }
            BitmapDrawable(resources, result)
        }
    }

    private fun getCurrentPath() = paths.lastOrNull()?.first
    private fun getPreviewPath() : Path {
        previewPaths = mutableListOf()
        previewPaths.add(Path().also { it.moveTo(startingX + 1, startingY + 1) } to Paint(pathPaint))
        return previewPaths.last().first
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (inEditMode) {
            when (event?.action) {
                MotionEvent.ACTION_DOWN -> {
                    handleTouchStart(event)
                    invalidate()
                }
                MotionEvent.ACTION_MOVE -> {
                    handleTouchMove(event)
                    invalidate()
                }
                MotionEvent.ACTION_UP -> {
                    handleTouchEnd()
                    countDrawn++
                    invalidate()
                }
            }
        }
        return true
    }

    private fun handleTouchStart(event: MotionEvent) {
        val sourceBitmap = super.getDrawable() ?: return

        val xTranslation = matrixValues[Matrix.MTRANS_X]
        val yTranslation = matrixValues[Matrix.MTRANS_Y]
        val scale = matrixValues[Matrix.MSCALE_X]

        val imageBounds = RectF(
                xTranslation,
                yTranslation,
                xTranslation + sourceBitmap.intrinsicWidth * scale,
                yTranslation + sourceBitmap.intrinsicHeight * scale)

        // make sure drawings are kept within the image bounds
        if (imageBounds.contains(event.x, event.y)) {
            when (currentBrush){
                BrushType.NORMAL, BrushType.EMBOSS, BrushType.BLUR, BrushType.ERASER -> {
                    paths.add(Path().also { it.moveTo(event.x + 1, event.y + 1) } to Paint(pathPaint))
                }
            }
            currentX = event.x
            currentY = event.y
            startingX = event.x
            startingY = event.y

        }
    }

    private fun handleTouchMove(event: MotionEvent) {
        val sourceBitmap = super.getDrawable() ?: return

        val xTranslation = matrixValues[Matrix.MTRANS_X]
        val yTranslation = matrixValues[Matrix.MTRANS_Y]
        val scale = matrixValues[Matrix.MSCALE_X]

        val xPos = event.x.coerceIn(xTranslation, xTranslation + sourceBitmap.intrinsicWidth * scale)
        val yPos = event.y.coerceIn(yTranslation, yTranslation + sourceBitmap.intrinsicHeight * scale)

        val dx = Math.abs(xPos - currentX)
        val dy = Math.abs(yPos - currentY)

        if (dx >= touchTolerance || dy >= touchTolerance) {

            when (currentBrush){
                BrushType.SHAPE_CIRCLE -> {
                    val radius = sqrt(Math.pow((currentX - startingX).toDouble(), 2.0))
                    getPreviewPath().addCircle(startingX, startingY, radius.toFloat() / 2, Path.Direction.CW)
                }
                BrushType.SHAPE_SQUARE -> {
                    getPreviewPath()?.addRect(startingX, startingY, currentX, currentY, Path.Direction.CW)
                }
                BrushType.NORMAL, BrushType.EMBOSS, BrushType.BLUR -> getCurrentPath()?.quadTo(currentX, currentY, (xPos + currentX) / 2, (yPos + currentY) / 2)
                BrushType.ERASER -> getCurrentPath()?.quadTo(currentX, currentY, (xPos + currentX) / 2, (yPos + currentY) / 2).also { pathPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
                else -> null
            }


            currentX = xPos
            currentY = yPos
        }
    }

    private fun handleTouchEnd() {
        when (currentBrush) {
            BrushType.SHAPE_CIRCLE -> {
                val radius = sqrt(Math.pow((currentX - startingX).toDouble(), 2.0))
                getCurrentPath()?.addCircle(startingX, startingY, radius.toFloat() / 2, Path.Direction.CW)
                previewPaths.clear()
            }
            BrushType.SHAPE_SQUARE -> {
                getCurrentPath()?.addRect(startingX, startingY, currentX, currentY, Path.Direction.CW)
                previewPaths.clear()
            }
            BrushType.NORMAL, BrushType.EMBOSS, BrushType.BLUR -> getCurrentPath()?.lineTo(currentX, currentY)
            else -> null
        }

    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        brushBitmap?.eraseColor(Color.TRANSPARENT)
        brushCanvas?.drawColor(Color.TRANSPARENT)
        canvas?.save()
        Log.d(TAG, "currentBrush: $currentBrush")
        Log.d("paths", "paths: ${paths.size}")
        for (index in paths.indices) {
            val path = paths[index]
            if (index >= countDrawn) {
                path.second.maskFilter =
                        when (currentBrush) {
                            BrushType.EMBOSS -> defaultEmboss
                            BrushType.BLUR -> defaultBlur
                            BrushType.NORMAL -> null
                            BrushType.SHAPE_CIRCLE -> null
                            BrushType.SHAPE_SQUARE -> null
                            else -> {
                                null}
                        }
            }
            brushCanvas?.drawPath(paths[index].first, paths[index].second)
        }
        Log.d("previewPaths", "previewPaths: ${previewPaths.size}")
        for (index in previewPaths.indices) {
            Log.d("previewPaths", "previewPaths: $index")
            brushCanvas?.drawPath(previewPaths[index].first, previewPaths[index].second)
        }
        canvas?.drawBitmap(brushBitmap, 0f, 0f, defaultBitmapPaint)
        canvas?.restore()
    }

    private fun disableEraser() {
        isInEraserMode = false
    }

    /**
     * Enable normal mode
     */
    fun normal() {
        disableEraser()
        currentBrush = BrushType.NORMAL
    }

    fun eraser() {
        isInEraserMode = true
        currentBrush = BrushType.ERASER
    }

    /**
     * Change brush type to emboss
     */
    fun emboss() {
        disableEraser()
        currentBrush = BrushType.EMBOSS
    }

    /**
     * Change brush type to blur
     */
    fun blur() {
        disableEraser()
        currentBrush = BrushType.BLUR
    }

    fun square() {
        disableEraser()
        currentBrush = BrushType.SHAPE_SQUARE
    }

    fun circle() {
        disableEraser()
        currentBrush = BrushType.SHAPE_CIRCLE
    }

    /**
     * Removes the last full path from the view.
     */
    fun undo() {
        paths.takeIf { it.isNotEmpty() }?.removeAt(paths.lastIndex)
        countDrawn--
        invalidate()
    }

    fun toggleFill(toggle: Boolean) {
        if(toggle) {
            pathPaint.style = Paint.Style.FILL
        } else {
            pathPaint.style = Paint.Style.STROKE
        }
    }

    /**
     * Returns true if any paths are currently drawn on the image, false otherwise.
     */
    fun isModified(): Boolean {
        return if (paths != null) {
            paths.isNotEmpty()
        } else {
            false
        }
    }

    /**
     * Clears all existing paths from the image.
     */
    fun clear() {
        paths.clear()
        countDrawn = 0
        invalidate()
    }


}