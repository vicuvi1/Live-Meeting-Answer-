package com.meeting.copilot.ui

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.meeting.copilot.App
import com.meeting.copilot.caption.Overlay
import com.meeting.copilot.util.Setup

class MainActivity : ComponentActivity() {

    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 && !Setup.notificationsAllowed(this)) {
            askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        val app = App.instance
        setContent {
            CopilotTheme {
                var tab by rememberSaveable { mutableIntStateOf(0) }
                val resumes = rememberResumeCount()
                Scaffold(
                    containerColor = Palette.Background,
                    bottomBar = {
                        Column {
                            Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Border))
                            NavigationBar(containerColor = Palette.Background, tonalElevation = 0.dp) {
                                val itemColors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Palette.Green,
                                    selectedTextColor = Palette.Green,
                                    indicatorColor = Palette.GreenDeep,
                                    unselectedIconColor = Palette.Muted,
                                    unselectedTextColor = Palette.Muted,
                                )
                                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Filled.Home, null) }, label = { Text("Acum") }, colors = itemColors)
                                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Filled.List, null) }, label = { Text("Istoric") }, colors = itemColors)
                                NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Filled.Settings, null) }, label = { Text("Setări") }, colors = itemColors)
                            }
                        }
                    },
                ) { padding ->
                    val modifier = Modifier.padding(padding)
                    when (tab) {
                        0 -> HomeScreen(app, resumes, onOpenSettings = { tab = 2 }, modifier = modifier)
                        1 -> HistoryScreen(app, modifier = modifier)
                        else -> SettingsScreen(app, resumes, modifier = modifier)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Overlay.appVisible.value = true
    }

    override fun onPause() {
        Overlay.appVisible.value = false
        super.onPause()
    }

    fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33) askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
}

/** Counts returns to the app, so setup checks refresh after visiting system settings. */
@Composable
fun rememberResumeCount(): Int {
    val owner = LocalLifecycleOwner.current
    var count by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) count++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return count
}
