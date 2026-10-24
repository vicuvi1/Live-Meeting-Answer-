package com.meeting.copilot.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** All user settings. [version] changes on every write so screens can refresh. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    var userName by text("user_name", "")
    /** Other forms people use: nickname, surname, "doamna X". Comma separated. */
    var aliases by text("aliases", "")
    var profileNotes by text("profile_notes", "")
    /** Colleagues' names, project names, terms: helps fix caption mistakes. */
    var vocabulary by text("vocabulary", "")
    var topics by text("topics", "")

    var provider by text("provider", PROVIDER_GEMINI)
    var geminiKey by text("gemini_key", "")
    /** Replaced automatically with Google's newest Flash model when this one is retired. */
    var geminiModel by text("gemini_model", "gemini-3.8-flash")
    var openRouterKey by text("openrouter_key", "")
    var openRouterModel by text("openrouter_model", "anthropic/claude-haiku-5.5")

    var vibrate by flag("vibrate", true)
    var whisperQuestion by flag("whisper_question", false)
    var showBubble by flag("show_bubble", true)
    /** Press the captions button in Zoom/Meet automatically and choose Romanian. */
    var autoCaptions by flag("auto_captions", true)
    /** HyperOS steps Android can't report back; she marks them done after opening them. */
    var xiaomiAutostartDone by flag("xiaomi_autostart_done", false)
    var xiaomiFloatingDone by flag("xiaomi_floating_done", false)
    var xiaomiRecentsDone by flag("xiaomi_recents_done", false)

    var diagnostics by flag("diagnostics", false)
    var diagnosticsAllApps by flag("diagnostics_all_apps", false)

    fun names(): List<String> =
        (listOf(userName) + aliases.split(',')).map { it.trim() }.filter { it.isNotEmpty() }

    fun topicList(): List<String> = topics.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    fun displayName(): String = userName.trim().ifEmpty { "utilizatoarea" }

    fun hasAiKey(): Boolean =
        if (provider == PROVIDER_OPENROUTER) openRouterKey.isNotBlank() else geminiKey.isNotBlank()

    private fun text(key: String, default: String) = object : ReadWriteProperty<Any?, String> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = sp.getString(key, default) ?: default
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
            sp.edit().putString(key, value).apply()
            _version.value++
        }
    }

    private fun flag(key: String, default: Boolean) = object : ReadWriteProperty<Any?, Boolean> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = sp.getBoolean(key, default)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            sp.edit().putBoolean(key, value).apply()
            _version.value++
        }
    }

    companion object {
        const val PROVIDER_GEMINI = "gemini"
        const val PROVIDER_OPENROUTER = "openrouter"
    }
}
