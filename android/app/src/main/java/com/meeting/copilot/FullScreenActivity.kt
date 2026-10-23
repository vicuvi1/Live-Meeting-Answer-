package com.meeting.copilot

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class FullScreenActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    companion object {
        private const val RECORD_AUDIO_REQUEST_CODE = 2001
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Full screen immersive layout
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        setContentView(R.layout.activity_fullscreen)
        webView = findViewById(R.id.webView)

        val serverUrl = intent.getStringExtra("SERVER_URL") ?: "http://10.0.2.2:5173"

        // Request audio recording permission if not granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_REQUEST_CODE)
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                // Grant microphone and audio capture inside the WebView
                request?.grant(request.resources)
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    val htmlError = """
                        <!DOCTYPE html>
                        <html lang="ro">
                        <head>
                            <meta name="viewport" content="width=device-width, initial-scale=1.0">
                            <style>
                                body { background: #030712; color: #f8fafc; font-family: -apple-system, sans-serif; display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 100vh; margin: 0; text-align: center; padding: 24px; box-sizing: border-box; }
                                .card { background: #0f172a; border: 1px solid #1e293b; padding: 28px 24px; border-radius: 24px; max-width: 360px; box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.5); }
                                h2 { color: #10b981; margin: 0 0 12px 0; font-size: 20px; }
                                p { font-size: 13px; color: #94a3b8; line-height: 1.6; margin: 0 0 20px 0; }
                                button { background: #059669; color: white; border: none; padding: 14px 24px; border-radius: 14px; font-weight: bold; font-size: 14px; width: 100%; cursor: pointer; transition: all 0.2s; }
                                button:active { transform: scale(0.97); }
                            </style>
                        </head>
                        <body>
                            <div class="card">
                                <h2>🔌 Conectare Copilot</h2>
                                <p>Asigurați-vă că serverul este pornit pe calculator și telefonul este conectat la aceeași rețea WiFi.</p>
                                <button onclick="location.reload()">🔄 Reîncearcă Conexiunea</button>
                            </div>
                        </body>
                        </html>
                    """.trimIndent()
                    view?.loadDataWithBaseURL(null, htmlError, "text/html", "UTF-8", null)
                }
            }
        }

        webView.loadUrl(serverUrl)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    finish()
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching {
            webView.stopLoading()
            webView.destroy()
        }
    }
}
