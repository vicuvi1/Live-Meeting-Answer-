package com.olivia.copilot

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
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

    // In-App Auto-Updater views
    private lateinit var cardUpdate: CardView
    private lateinit var txtCurrentVersion: TextView
    private lateinit var txtUpdateStatus: TextView
    private lateinit var progressUpdate: ProgressBar
    private lateinit var btnCheckOrUpdate: Button
    private var availableUpdate: UpdateInfo? = null
    private val currentAppVersion = "1.0.0"

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

        // Setup Xiaomi HyperOS specific permission helpers
        setupXiaomiHyperOSHelpers()

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

        // Setup In-App Auto-Updater from GitHub
        setupUpdateManager()

        // Subscription Manager Button
        val btnOpenSub = findViewById<Button?>(R.id.btnOpenSubModal)
        btnOpenSub?.setOnClickListener {
            savePreferences()
            val intent = Intent(this, FullScreenActivity::class.java).apply {
                val base = editServerUrl.text.toString().trim()
                val urlToLoad = if (base.endsWith(":8000")) base.replace(":8000", ":5173") else base
                putExtra("SERVER_URL", urlToLoad)
            }
            startActivity(intent)
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

    private fun setupXiaomiHyperOSHelpers() {
        val btnXiaomiPopup = findViewById<Button?>(R.id.btnXiaomiPopup)
        val btnXiaomiBattery = findViewById<Button?>(R.id.btnXiaomiBattery)
        val btnXiaomiAutostart = findViewById<Button?>(R.id.btnXiaomiAutostart)
        val cardXiaomi = findViewById<View?>(R.id.cardXiaomiSettings)

        // Highlight if Xiaomi / Redmi / POCO
        val isXiaomi = Build.MANUFACTURER.contains("Xiaomi", ignoreCase = true) ||
                       Build.MANUFACTURER.contains("Redmi", ignoreCase = true) ||
                       Build.MANUFACTURER.contains("POCO", ignoreCase = true)

        if (cardXiaomi != null) {
            cardXiaomi.visibility = View.VISIBLE
        }

        // 1. Xiaomi "Display pop-up windows while running in the background"
        btnXiaomiPopup?.setOnClickListener {
            try {
                val intent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                    setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                    putExtra("extra_pkgname", packageName)
                }
                startActivity(intent)
            } catch (e: Exception) {
                // Fallback to standard App Details
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
            Toast.makeText(this, "Activează 'Afișează ferestre pop-up în fundal'", Toast.LENGTH_LONG).show()
        }

        // 2. Battery Saver -> No Restrictions (Fără restricții)
        btnXiaomiBattery?.setOnClickListener {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                    if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        startActivity(intent)
                    } else {
                        Toast.makeText(this, "Bateria este deja configurată pe 'Fără Restricții'!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(intent)
            }
        }

        // 3. Xiaomi Autostart Manager
        btnXiaomiAutostart?.setOnClickListener {
            try {
                val intent = Intent().apply {
                    component = ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                }
                startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_SETTINGS)
                startActivity(intent)
            }
            Toast.makeText(this, "Activează 'Pornire automată' pentru Olivia Copilot", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupUpdateManager() {
        cardUpdate = findViewById(R.id.cardUpdate)
        txtCurrentVersion = findViewById(R.id.txtCurrentVersion)
        txtUpdateStatus = findViewById(R.id.txtUpdateStatus)
        progressUpdate = findViewById(R.id.progressUpdate)
        btnCheckOrUpdate = findViewById(R.id.btnCheckOrUpdate)

        txtCurrentVersion.text = "v$currentAppVersion"

        btnCheckOrUpdate.setOnClickListener {
            if (availableUpdate != null && availableUpdate?.hasUpdate == true) {
                startUpdateDownload(availableUpdate!!)
            } else {
                checkForUpdates(manual = true)
            }
        }

        // Silent automatic check on app open
        checkForUpdates(manual = false)
    }

    private fun checkForUpdates(manual: Boolean) {
        if (manual) {
            txtUpdateStatus.text = "Se caută actualizări pe GitHub..."
            btnCheckOrUpdate.isEnabled = false
        }

        scope.launch {
            val result = UpdateManager.checkForUpdate(currentAppVersion)
            btnCheckOrUpdate.isEnabled = true

            result.onSuccess { info ->
                if (info.hasUpdate) {
                    availableUpdate = info
                    cardUpdate.setCardBackgroundColor(Color.parseColor("#065F46"))
                    txtUpdateStatus.text = "🎉 O nouă versiune (${info.latestVersion}) este disponibilă!"
                    btnCheckOrUpdate.text = "⚡ Actualizează Acum la ${info.latestVersion}"
                    btnCheckOrUpdate.setBackgroundColor(Color.parseColor("#10B981"))
                    if (manual) {
                        Toast.makeText(this@MainActivity, "Actualizare găsită: ${info.latestVersion}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    availableUpdate = null
                    txtUpdateStatus.text = "✓ Ești la zi cu ultima versiune (v$currentAppVersion)"
                    btnCheckOrUpdate.text = "🔍 Verifică Actualizări GitHub"
                    btnCheckOrUpdate.setBackgroundColor(Color.parseColor("#059669"))
                    if (manual) {
                        Toast.makeText(this@MainActivity, "Aplicația este la zi!", Toast.LENGTH_SHORT).show()
                    }
                }
            }.onFailure { err ->
                if (manual) {
                    txtUpdateStatus.text = "✕ Verificare eșuată (${err.localizedMessage ?: "rețea"})"
                    btnCheckOrUpdate.text = "🔄 Reîncearcă Verificarea"
                }
            }
        }
    }

    private fun startUpdateDownload(info: UpdateInfo) {
        btnCheckOrUpdate.isEnabled = false
        btnCheckOrUpdate.text = "⏳ Se descarcă..."
        progressUpdate.visibility = View.VISIBLE
        progressUpdate.isIndeterminate = false
        progressUpdate.progress = 0
        txtUpdateStatus.text = "Se descarcă versiunea ${info.latestVersion}..."

        scope.launch {
            UpdateManager.downloadAndInstallApk(
                activity = this@MainActivity,
                apkUrl = info.apkUrl,
                onProgress = { percent ->
                    progressUpdate.progress = percent
                    txtUpdateStatus.text = "Descărcat: $percent%..."
                },
                onError = { errorMsg ->
                    progressUpdate.visibility = View.GONE
                    btnCheckOrUpdate.isEnabled = true
                    btnCheckOrUpdate.text = "⚡ Reîncearcă Actualizarea"
                    txtUpdateStatus.text = "✕ $errorMsg"
                    Toast.makeText(this@MainActivity, errorMsg, Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
