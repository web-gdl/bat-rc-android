package org.batrc.controller.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.batrc.controller.MainViewModel
import org.batrc.controller.net.LinkState
import org.batrc.controller.video.CarVideo
import org.batrc.controller.video.VideoStatus

@Composable
fun ControllerScreen(viewModel: MainViewModel) {
    val link by viewModel.linkState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.state.collectAsStateWithLifecycle()
    val foreground by viewModel.foreground.collectAsStateWithLifecycle()
    val headlights by viewModel.headlights.collectAsStateWithLifecycle()
    val smoke by viewModel.smoke.collectAsStateWithLifecycle()
    val videoStatus by viewModel.videoStatus.collectAsStateWithLifecycle()
    val lastPacket by viewModel.lastPacket.collectAsStateWithLifecycle()
    val sendError by viewModel.sendError.collectAsStateWithLifecycle()

    var showSettings by rememberSaveable { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current

    val vibrate = settings.haptics
    val press: (() -> Unit) -> Unit = { action ->
        if (vibrate) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        action()
    }

    val linkNow = link
    val connected = linkNow as? LinkState.Connected

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (connected != null && foreground) {
            CarVideo(
                network = connected.network,
                overTcp = settings.videoOverTcp,
                onStatus = viewModel::onVideoStatus,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(12.dp),
        ) {
            // Fila superior: funciones del vehículo y ajustes.
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionButton("Luces", onClick = { press(viewModel::toggleHeadlights) }, active = headlights)
                ActionButton("Humo", onClick = { press(viewModel::toggleSmoke) }, active = smoke)
                ActionButton("Suspensión", onClick = { press(viewModel::toggleSuspension) })
                ActionButton("Cañón ↕", onClick = { press(viewModel::toggleCannonLift) })
                Spacer(Modifier.weight(1f))
                ActionButton("Ajustes", onClick = { showSettings = true })
            }

            // Mandos de conducción.
            val throttleCorner = if (settings.swapSides) Alignment.BottomEnd else Alignment.BottomStart
            val steeringCorner = if (settings.swapSides) Alignment.BottomStart else Alignment.BottomEnd
            AxisPad(
                vertical = true,
                label = "Acelerador",
                onValue = viewModel::setThrottle,
                modifier = Modifier
                    .align(throttleCorner)
                    .size(150.dp),
            )
            AxisPad(
                vertical = false,
                label = "Dirección",
                onValue = viewModel::setSteering,
                modifier = Modifier
                    .align(steeringCorner)
                    .size(150.dp),
            )

            // Armas y sonido, entre los dos mandos.
            Row(
                modifier = Modifier.align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ActionButton("Motor", onClick = { press(viewModel::revEngine) })
                ActionButton("Ametr.", onClick = { press(viewModel::fireMachineGun) })
                ActionButton("Cañón", onClick = { press(viewModel::fireCannon) })
                ActionButton("Misiles", onClick = { press(viewModel::fireMissiles) })
            }

            // Estado de la conexión, en el centro.
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    connected == null -> ConnectCard(
                        link = linkNow,
                        onOpenWifi = { openWifiSettings(context) },
                    )

                    videoStatus is VideoStatus.Connecting || videoStatus is VideoStatus.Idle ->
                        StatusText("Conectando con la cámara…")

                    videoStatus is VideoStatus.Error ->
                        StatusText("Sin vídeo (${(videoStatus as VideoStatus.Error).detail}). Reintentando…")
                }
            }

            if (settings.showDiagnostics) {
                Text(
                    text = sendError?.let { "Error de envío: $it" } ?: "UDP → $lastPacket",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 56.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }

    if (showSettings) {
        SettingsDialog(viewModel = viewModel, onClose = { showSettings = false })
    }
}

@Composable
private fun ConnectCard(link: LinkState, onOpenWifi: () -> Unit) {
    Column(
        modifier = Modifier
            .widthIn(max = 300.dp)
            .background(Panel.copy(alpha = 0.92f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Vehículo no conectado",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        val detail = if (link is LinkState.OtherWifi) {
            "Estás en otra red Wi-Fi (${link.localAddress ?: "sin IP"}). " +
                "Elige la red del vehículo."
        } else {
            "Enciende el vehículo y conecta el teléfono a su red Wi-Fi. " +
                "Si Android avisa de que la red no tiene internet, elige mantener la conexión."
        }
        Text(text = detail, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, textAlign = TextAlign.Center)
        ActionButton("Abrir Wi-Fi", onClick = onOpenWifi, active = true)
    }
}

@Composable
private fun StatusText(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 13.sp,
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

private fun openWifiSettings(context: Context) {
    val panel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // Panel flotante de Wi-Fi: no hace falta salir de la app.
        Intent(Settings.Panel.ACTION_WIFI)
    } else {
        Intent(Settings.ACTION_WIFI_SETTINGS)
    }
    try {
        context.startActivity(panel)
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        } catch (ignored: ActivityNotFoundException) {
            // El dispositivo no expone los ajustes de Wi-Fi.
        }
    }
}
