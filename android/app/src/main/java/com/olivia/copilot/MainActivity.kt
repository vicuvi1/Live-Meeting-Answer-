package com.olivia.copilot

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var btnToggle: Button
    private var isServiceRunning = false

    companion object {
        private const val OVERLAY_PERMISSION_REQ_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnToggle = findViewById(R.id.btnToggleService)

        btnToggle.setOnClickListener {
            if (!hasOverlayPermission()) {
                requestOverlayPermission()
            } else {
                toggleFloatingService()
            }
        }
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE)
            Toast.makeText(this, R.string.txt_permission_needed, Toast.LENGTH_LONG).show()
        }
    }

    private fun toggleFloatingService() {
        val intent = Intent(this, FloatingBubbleService::class.java)
        if (!isServiceRunning) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            btnToggle.setText(R.string.btn_stop_overlay)
            btnToggle.setBackgroundColor(getColor(R.color.rose_500))
            isServiceRunning = true
            Toast.makeText(this, "Asistentul Olivia este acum activ peste Zoom!", Toast.LENGTH_SHORT).show()
        } else {
            stopService(intent)
            btnToggle.setText(R.string.btn_start_overlay)
            btnToggle.setBackgroundColor(getColor(R.color.emerald_600))
            isServiceRunning = false
        }
    }
}
