package org.batrc.controller.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.batrc.controller.MainViewModel
import org.batrc.controller.protocol.Protocol
import kotlin.math.roundToInt

@Composable
fun SettingsDialog(viewModel: MainViewModel, onClose: () -> Unit) {
    val settings by viewModel.settings.state.collectAsStateWithLifecycle()
    val deviceInfo by viewModel.deviceInfo.collectAsStateWithLifecycle()
    val store = viewModel.settings

    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("Cerrar") } },
        title = { Text("Ajustes") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SectionTitle("Conducción")
                SwitchRow("Invertir adelante / atrás", settings.invertThrottle) { on ->
                    store.update { it.copy(invertThrottle = on) }
                }
                SwitchRow("Invertir izquierda / derecha", settings.invertSteering) { on ->
                    store.update { it.copy(invertSteering = on) }
                }
                SwitchRow("Acelerador a la derecha", settings.swapSides) { on ->
                    store.update { it.copy(swapSides = on) }
                }
                Text("Potencia máxima: ${settings.maxPower} %", fontSize = 14.sp)
                Slider(
                    value = settings.maxPower.toFloat(),
                    onValueChange = { value -> store.update { it.copy(maxPower = value.roundToInt()) } },
                    valueRange = 30f..100f,
                )

                SectionTitle("Ajuste de la dirección")
                Text(
                    "Si el vehículo se desvía al ir recto, corrígelo paso a paso.",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("◀ −1", onClick = { viewModel.calibrateSteering(-1) })
                    ActionButton("Restablecer", onClick = { viewModel.calibrateSteering(0) })
                    ActionButton("+1 ▶", onClick = { viewModel.calibrateSteering(1) })
                }

                SectionTitle("Efectos")
                SwitchRow("Efectos visuales al disparar", settings.visualEffects) { on ->
                    store.update { it.copy(visualEffects = on) }
                }
                SwitchRow("Sonidos en el teléfono", settings.phoneSounds) { on ->
                    store.update { it.copy(phoneSounds = on) }
                }
                SwitchRow("Mostrar mira", settings.showCrosshair) { on ->
                    store.update { it.copy(showCrosshair = on) }
                }
                SwitchRow("Modo objetivos (blancos virtuales)", settings.targetMode && settings.visualEffects) { on ->
                    store.update { it.copy(targetMode = on, visualEffects = it.visualEffects || on) }
                }
                Text(
                    "En modo objetivos, apunta moviendo los mandos: la mira se pone roja sobre un blanco.",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f),
                )

                SectionTitle("Vídeo")
                SwitchRow("Forzar vídeo por TCP", settings.videoOverTcp) { on ->
                    store.update { it.copy(videoOverTcp = on) }
                }

                SectionTitle("General")
                SwitchRow("Vibración", settings.haptics) { on ->
                    store.update { it.copy(haptics = on) }
                }
                SwitchRow("Mostrar diagnóstico UDP", settings.showDiagnostics) { on ->
                    store.update { it.copy(showDiagnostics = on) }
                }

                SectionTitle("Prueba de sonidos")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Protocol.TEST_SOUNDS.forEach { number ->
                        ActionButton(number.toString(), onClick = { viewModel.playSound(number) })
                    }
                }

                SectionTitle("Vehículo")
                Text(
                    text = deviceInfo?.takeIf { it.isNotBlank() } ?: "Sin datos (no conectado).",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f),
                )
                Text(
                    text = "App no oficial. No está afiliada a Mattel ni a DC.",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = Accent,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 10.dp),
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
