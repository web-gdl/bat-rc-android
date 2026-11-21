package org.batrc.controller

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.batrc.controller.ui.BatRcTheme
import org.batrc.controller.ui.ControllerScreen

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Usa también la zona de la muesca de la cámara.
            val attributes = window.attributes
            attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = attributes
        }

        // Solo en compilaciones de depuración: congela una escena de efectos
        // para generar capturas (ver tools/capturas.sh).
        if (BuildConfig.DEBUG && savedInstanceState == null) {
            intent.getStringExtra(EXTRA_DEMO)?.let { scene ->
                viewModel.startDemo(scene, intent.getFloatExtra(EXTRA_DEMO_TIME, 0.3f).toDouble())
            }
        }

        setContent {
            BatRcTheme {
                ControllerScreen(viewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onForeground(true)
    }

    override fun onStop() {
        // Para el vehículo y libera el Wi-Fi al salir de la app.
        viewModel.onForeground(false)
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private companion object {
        const val EXTRA_DEMO = "fx_demo"
        const val EXTRA_DEMO_TIME = "fx_time"
    }

    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}
