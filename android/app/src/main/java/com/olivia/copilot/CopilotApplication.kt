package com.olivia.copilot

import android.app.Application
import android.content.Intent
import android.os.Process
import android.util.Log

/**
 * CopilotApplication implements a global Never-Crash UncaughtExceptionHandler.
 * If any unexpected runtime exception escapes the main thread or worker threads,
 * this handler intercepts it, logs the diagnostic trace, and prevents the jarring
 * Android "App has stopped" crash dialog from appearing to Olivia during a live meeting.
 */
class CopilotApplication : Application() {

    companion object {
        private const val TAG = "CopilotNeverCrash"
    }

    override fun onCreate() {
        super.onCreate()
        setupGlobalCrashInterceptor()
    }

    private fun setupGlobalCrashInterceptor() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "[NEVER-CRASH INTERCEPTED] Uncaught exception on thread: ${thread.name}", throwable)

            // Attempt graceful fallback: Restart MainActivity if the main UI thread collapsed
            try {
                val intent = Intent(applicationContext, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    putExtra("CRASH_RECOVERED", true)
                }
                startActivity(intent)
            } catch (recoveryEx: Exception) {
                Log.e(TAG, "Failed to auto-restart MainActivity after crash interception", recoveryEx)
            }

            // Terminate cleanly to prevent lingering corrupt state, without showing system crash popup
            Process.killProcess(Process.myPid())
            System.exit(10)
        }
    }
}
