package app.ohrhachaim.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import app.ohrhachaim.android.ui.OhrRoot

class MainActivity : ComponentActivity() {
    private val model: OhrViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(0xFF1C1612.toInt()),
            navigationBarStyle = SystemBarStyle.dark(0xFF141210.toInt()),
        )
        setContent {
            LaunchedEffect(Unit) { model.applyLaunch(intent) }
            OhrRoot(model)
        }
    }
}
