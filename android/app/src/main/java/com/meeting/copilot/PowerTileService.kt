package com.meeting.copilot

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * The on/off button in the quick panel (swipe down from the top): she can switch the assistant
 * on before a meeting without opening the app.
 */
class PowerTileService : TileService() {

    override fun onStartListening() {
        render()
    }

    override fun onClick() {
        val app = App.instance
        app.engine.setActive(!app.prefs.active)
        render()
    }

    private fun render() {
        val tile = qsTile ?: return
        val on = App.instance.prefs.active
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.app_name)
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = if (on) "Pornit" else "Oprit"
        tile.updateTile()
    }

    companion object {
        /** Asks Android to redraw the tile after the switch changed somewhere else. */
        fun refresh(context: Context) {
            try {
                requestListeningState(context, ComponentName(context, PowerTileService::class.java))
            } catch (_: Exception) {
            }
        }

        /** Android 13+: one tap to place the button in the quick panel. Result text in Romanian. */
        fun requestAdd(context: Context, onResult: (String) -> Unit) {
            if (Build.VERSION.SDK_INT < 33) {
                onResult("Trage în jos panoul de sus → creionul de editare → adaugă „Asistent Ședințe”.")
                return
            }
            val manager = context.getSystemService(StatusBarManager::class.java) ?: return
            manager.requestAddTileService(
                ComponentName(context, PowerTileService::class.java),
                context.getString(R.string.app_name),
                Icon.createWithResource(context, R.drawable.ic_tile),
                context.mainExecutor,
            ) { result ->
                onResult(
                    when (result) {
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "Adăugat: trage în jos panoul de sus ca să-l vezi."
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "Butonul e deja în panoul de sus."
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> "Nu a fost adăugat."
                        else -> "Telefonul nu permite adăugarea automată. Trage în jos panoul de sus → editează → adaugă „Asistent Ședințe”."
                    }
                )
            }
        }
    }
}
