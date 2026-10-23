package com.olivia.copilot

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var btnToggle: Button
    private lateinit var btnOpenFullscreen: Button
    private lateinit var btnTestConnection: Button
    private lateinit var editServerUrl: EditText
    private lateinit var editTargetName: EditText
    private lateinit var txtStatus: TextView
    private lateinit var prefs: SharedPreferences

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var isServiceRunning = false

    companion object {
        private const val OVERLAY_PERMISSION_REQ_CODE = 1001
        private const val NOTIFICATION_PERMISSION_REQ_CODE = 1002
        private const val PREFS_NAME = "OliviaCopilotPrefs"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_TARGET_NAME = "target_name"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        btnToggle = findViewById(R.id.btnToggleService)
        btnOpenFullscreen = findViewById(R.id.btnOpenFullscreen)
        btnTestConnection = findViewById(R.id.btnTestConnection)
        editServerUrl = findViewById(R.id.editServerUrl)
        editTargetName = findViewById(R.id.editTargetName)
        txtStatus = findViewById(R.id.txtConnectionStatus)

        // Load saved values or sensible defaults
        val savedUrl = prefs.getString(KEY_SERVER_URL, "http://10.0.2.2:8000")
        val savedName = prefs.getString(KEY_TARGET_NAME, "Olivia")
        editServerUrl.setText(savedUrl)
        editTargetName.setText(savedName)

        // Check Notification Permission on Android 13+
        checkNotificationPermission()

        btnOpenFullscreen.setOnClickListener {
            savePreferences()
            val intent = Intent(this, FullScreenActivity::class.java).apply {
                val base = editServerUrl.text.toString().trim()
                // Frontend is served on port 5173 or base
                val urlToLoad = if (base.endsWith(":8000")) base.replace(":8000", ":5173") else base
                putExtra("SERVER_URL", urlToLoad)
            }
            startActivity(intent)
        }

        btnTestConnection.setOnClickListener {
            testServerConnection()
        }

        btnToggle.setOnClickListener {
            savePreferences()
            if (!hasOverlayPermission()) {
                requestOverlayPermission()
            } else {
                toggleFloatingService()
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQ_CODE
                )
            }
        }
    }

    private fun testServerConnection() {
        val url = editServerUrl.text.toString().trim().removeSuffix("/")
        txtStatus.text = "Status: Se verifică..."
        txtStatus.setTextColor(ContextCompat.getColor(this, R.color.amber_500))

        scope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(4, TimeUnit.SECONDS)
                    .readTimeout(4, TimeUnit.SECONDS)
                    .build()

                val request = Request.Builder()
                    .url("$url/api/health")
                    .build()

                val response = client.newCall(request).execute()
                val isOk = response.isSuccessful

                withContext(Dispatchers.Main) {
                    if (isOk) {
                        txtStatus.text = "Status: ✓ Server Conectat cu Succes!"
                        txtStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.emerald_500))
                        savePreferences()
                    } else {
                        txtStatus.text = "Status: ✕ Serverul a returnat codul ${response.code}"
                        txtStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.rose_500))
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    txtStatus.text = "Status: ✕ Conexiune eșuată (${e.localizedMessage ?: "timeout"})"
                    txtStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.rose_500))
                }
            }
        }
    }

    private fun savePreferences() {
        prefs.edit()
            .putString(KEY_SERVER_URL, editServerUrl.text.toString().trim())
            .putString(KEY_TARGET_NAME, editTargetName.text.toString().trim())
            .apply()
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
        val intent = Intent(this, FloatingBubbleService::class.java).apply {
            putExtra("SERVER_URL", editServerUrl.text.toString().trim())
            putExtra("TARGET_NAME", editTargetName.text.toString().trim())
        }

        if (!isServiceRunning) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            btnToggle.setText(R.string.btn_stop_overlay)
            btnToggle.setBackgroundColor(ContextCompat.getColor(this, R.color.rose_500))
            isServiceRunning = true
            Toast.makeText(this, "Bula plutitoare Olivia este acum activă peste Zoom!", Toast.LENGTH_SHORT).show()
        } else {
            stopService(intent)
            btnToggle.setText(R.string.btn_start_overlay)
            btnToggle.setBackgroundColor(ContextCompat.getColor(this, R.color.emerald_600))
            isServiceRunning = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
