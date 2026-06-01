package com.deviceb.inkcapture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.max
import kotlin.math.min

class InkCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var bitmap: Bitmap? = null
    private var bitmapCanvas: Canvas? = null
    private val path = Path()

    private var currentColor: Int = Color.BLACK
    private var currentStrokeWidth: Float = 6f
    private var eraserMode: Boolean = false

    private var scaleFactor = 1f
    private var translateX = 0f
    private var translateY = 0f
    private var lastPan = PointF()
    private var isPanning = false

    private val inverseMatrix = Matrix()
    private val drawMatrix = Matrix()

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val oldScale = scaleFactor
            scaleFactor = (scaleFactor * detector.scaleFactor).coerceIn(0.5f, 6f)
            val focusX = detector.focusX
            val focusY = detector.focusY
            translateX = focusX - (focusX - translateX) * (scaleFactor / oldScale)
            translateY = focusY - (focusY - translateY) * (scaleFactor / oldScale)
            invalidate()
            return true
        }
    })

    private val penPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val bitmapPaint = Paint(Paint.DITHER_FLAG)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w <= 0 || h <= 0) return
        if (bitmap == null) {
            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bitmapCanvas = Canvas(bitmap!!)
            bitmapCanvas!!.drawColor(Color.WHITE)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.translate(translateX, translateY)
        canvas.scale(scaleFactor, scaleFactor)
        bitmap?.let { canvas.drawBitmap(it, 0f, 0f, bitmapPaint) }
        canvas.drawPath(path, penPaint)
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        if (event.pointerCount >= 2) {
            when (event.actionMasked) {
                MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_DOWN -> {
                    isPanning = true
                    lastPan.set(event.x, event.y)
                    path.reset()
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!scaleDetector.isInProgress) {
                        val dx = event.x - lastPan.x
                        val dy = event.y - lastPan.y
                        translateX += dx
                        translateY += dy
                        lastPan.set(event.x, event.y)
                        invalidate()
                    }
                }
                MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isPanning = false
                }
            }
            return true
        }

        if (isPanning) return true

        val pt = screenToCanvas(event.x, event.y)
        val x = pt.x
        val y = pt.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                updatePaint()
                path.reset()
                path.moveTo(x, y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.historySize) {
                    val hp = screenToCanvas(event.getHistoricalX(i), event.getHistoricalY(i))
                    path.lineTo(hp.x, hp.y)
                }
                path.lineTo(x, y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                path.lineTo(x, y)
                bitmapCanvas?.drawPath(path, penPaint)
                path.reset()
                invalidate()
                return true
            }
        }
        return true
    }

    private fun screenToCanvas(x: Float, y: Float): PointF {
        drawMatrix.reset()
        drawMatrix.postScale(scaleFactor, scaleFactor)
        drawMatrix.postTranslate(translateX, translateY)
        drawMatrix.invert(inverseMatrix)
        val pts = floatArrayOf(x, y)
        inverseMatrix.mapPoints(pts)
        return PointF(pts[0], pts[1])
    }

    private fun updatePaint() {
        penPaint.color = if (eraserMode) Color.WHITE else currentColor
        penPaint.strokeWidth = if (eraserMode) currentStrokeWidth * 2f else currentStrokeWidth
    }

    fun setInkColor(color: Int) {
        eraserMode = false
        currentColor = color
        updatePaint()
    }

    fun setNibSize(px: Float) {
        currentStrokeWidth = px.coerceIn(1f, 40f)
        updatePaint()
    }

    fun setEraser() {
        eraserMode = true
        updatePaint()
    }

    fun resetZoom() {
        scaleFactor = 1f
        translateX = 0f
        translateY = 0f
        invalidate()
    }

    fun clear() {
        bitmapCanvas?.drawColor(Color.WHITE)
        path.reset()
        invalidate()
    }

    fun exportBitmap(): Bitmap? {
        return bitmap?.copy(Bitmap.Config.ARGB_8888, false)
    }
}
