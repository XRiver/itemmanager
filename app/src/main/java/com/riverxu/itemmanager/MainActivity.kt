package com.riverxu.itemmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.riverxu.itemmanager.di.AppContainer
import com.riverxu.itemmanager.ui.OpenItemManagerApp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as ItemManagerApp
        val container: AppContainer = app.container

        setContent {
            OpenItemManagerApp(container)
        }
    }
}
