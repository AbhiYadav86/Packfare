package com.packfare.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.packfare.app.ui.PackfareNavHost
import com.packfare.app.ui.theme.PackfareTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as PackfareApp
        setContent {
            PackfareTheme {
                PackfareNavHost(app)
            }
        }
    }
}
