package com.cambiopro.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cambiopro.app.data.local.AppSettings
import com.cambiopro.app.navigation.AppRoot
import com.cambiopro.app.ui.theme.CambioTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as CambioApp).container
        setContent {
            val settings by container.userPrefs.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            CambioTheme(settings.theme) { AppRoot(container) }
        }
    }
}
