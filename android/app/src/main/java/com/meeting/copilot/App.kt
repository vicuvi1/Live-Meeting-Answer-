package com.meeting.copilot

import android.app.Application
import com.meeting.copilot.ai.LlmClient
import com.meeting.copilot.data.Db
import com.meeting.copilot.data.Prefs
import com.meeting.copilot.engine.Alerts
import com.meeting.copilot.engine.MeetingEngine
import com.meeting.copilot.util.DiagLog
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class App : Application() {
    lateinit var prefs: Prefs
        private set
    lateinit var db: Db
        private set
    lateinit var llm: LlmClient
        private set
    lateinit var engine: MeetingEngine
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        DiagLog.init(this)
        // Record crashes for the diagnostic export, then let Android handle them normally.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            DiagLog.recordCrash(thread, error)
            previous?.uncaughtException(thread, error)
        }
        prefs = Prefs(this)
        db = Db(this)
        llm = LlmClient(prefs)
        engine = MeetingEngine(prefs, db, llm, Alerts(this, prefs))
        // Keep the quick-panel on/off button in sync when the switch changes in the app.
        var lastActive = prefs.active
        MainScope().launch {
            prefs.version.collect {
                if (prefs.active != lastActive) {
                    lastActive = prefs.active
                    PowerTileService.refresh(this@App)
                }
            }
        }
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
