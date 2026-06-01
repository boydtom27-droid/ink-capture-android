package com.deviceb.inkcapture

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private lateinit var canvas: InkCanvasView
    private lateinit var status: TextView
    private lateinit var destination: Spinner
    private lateinit var sizeSpinner: Spinner

    private val relayBase = "https://device-b-relay.onrender.com"
    private val relayToken = "abc123xyz789"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        canvas = InkCanvasView(this)

        destination = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("sort_later", "thought_bank", "wall_display")
            )
        }

        sizeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("2", "4", "6", "10", "16")
            )
            setSelection(2)
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    canvas.setNibSize(selectedItem.toString().toFloat())
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
        }

        val blackButton = Button(this).apply { text = "Black"; setOnClickListener { canvas.setInkColor(Color.BLACK); status.text = "Black ink" } }
        val redButton = Button(this).apply { text = "Red"; setOnClickListener { canvas.setInkColor(Color.rgb(210, 0, 0)); status.text = "Red ink" } }
        val eraserButton = Button(this).apply { text = "Erase"; setOnClickListener { canvas.setEraser(); status.text = "Eraser" } }
        val zoomButton = Button(this).apply { text = "1:1"; setOnClickListener { canvas.resetZoom(); status.text = "Zoom reset" } }

        val clearButton = Button(this).apply {
            text = "Clear"
            setOnClickListener { canvas.clear(); status.text = "Canvas cleared" }
        }

        val sendButton = Button(this).apply {
            text = "Send"
            setOnClickListener { sendCurrentCanvas() }
        }

        status = TextView(this).apply {
            text = "Ready. One finger/S Pen draws; two fingers pan; pinch zooms."
            textSize = 14f
            setPadding(8, 6, 8, 8)
        }

        val toolbar1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8, 8, 8, 4)
            addView(destination, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(clearButton)
            addView(sendButton)
        }

        val toolbar2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8, 4, 8, 8)
            addView(blackButton)
            addView(redButton)
            addView(eraserButton)
            addView(TextView(this@MainActivity).apply { text = "  nib " })
            addView(sizeSpinner, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(zoomButton)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(toolbar1, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(toolbar2, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(canvas, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(status, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }

        setContentView(root)
    }

    private fun sendCurrentCanvas() {
        val bmp = canvas.exportBitmap()
        if (bmp == null) {
            status.text = "No bitmap available"
            return
        }
        status.text = "Sending..."
        thread {
            try {
                val result = uploadBitmap(bmp, destination.selectedItem.toString())
                runOnUiThread { status.text = result; DeviceBWidgetUtil.updateAllWidgets(this@MainActivity) }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Upload failed: ${e.javaClass.simpleName}: ${e.message}" }
            }
        }
    }

    private fun uploadBitmap(bitmap: Bitmap, dest: String): String {
        val boundary = "----InkCaptureBoundary${System.currentTimeMillis()}"
        val url = URL("$relayBase/api/ink_capture?token=$relayToken")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

        val pngBytes = ByteArrayOutputStream().use { baos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
            baos.toByteArray()
        }

        DataOutputStream(conn.outputStream).use { out ->
            fun writeLine(s: String) { out.writeBytes(s); out.writeBytes("\r\n") }
            writeLine("--$boundary")
            writeLine("Content-Disposition: form-data; name=\"destination\"")
            writeLine("")
            writeLine(dest)
            writeLine("--$boundary")
            writeLine("Content-Disposition: form-data; name=\"image\"; filename=\"ink.png\"")
            writeLine("Content-Type: image/png")
            writeLine("")
            out.write(pngBytes)
            writeLine("")
            writeLine("--$boundary--")
        }

        val code = conn.responseCode
        val response = try { conn.inputStream.bufferedReader().readText() } catch (_: Exception) { conn.errorStream?.bufferedReader()?.readText() ?: "" }
        conn.disconnect()
        return "HTTP $code: $response"
    }
}
