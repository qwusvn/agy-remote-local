package com.example.agyremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.agyremote.data.AppTheme
import com.example.agyremote.data.ConnectionConfig
import com.example.agyremote.data.ConnectionPreferences
import com.example.agyremote.theme.AGYRemoteTheme
import com.example.agyremote.ui.MainScreen

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    com.example.agyremote.service.AgyNotificationService.ensureChannelsCreated(this)
    setContent {
      val context = LocalContext.current
      val preferences = remember { ConnectionPreferences(context) }
      val config by preferences.configFlow.collectAsState(initial = ConnectionConfig())

      val isDark = when (config.theme) {
        AppTheme.SYSTEM -> isSystemInDarkTheme()
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
      }

      AGYRemoteTheme(darkTheme = isDark) {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          MainScreen(config = config, preferences = preferences, isDarkTheme = isDark)
        }
      }
    }
  }
}
