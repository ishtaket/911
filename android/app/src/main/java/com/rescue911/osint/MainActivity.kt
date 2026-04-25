package com.rescue911.osint

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rescue911.osint.navigation.Rescue911Nav
import com.rescue911.osint.ui.theme.Rescue911Theme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Rescue911Theme {
                Rescue911Nav()
            }
        }
    }
}
