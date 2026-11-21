package org.batrc.controller.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.batrc.controller.protocol.Axis

/** Fracción del tamaño del mando que equivale al recorrido completo. */
private const val TRAVEL = 0.34f

/**
 * Mando de un eje con retorno al centro.
 *
 * El punto donde se apoya el dedo es el neutro; al deslizar se obtiene un
 * valor entre -1 y 1 (arriba / derecha son positivos). Al soltar vuelve a 0.
 */
@Composable
fun AxisPad(
    vertical: Boolean,
    label: String,
    onValue: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var value by remember { mutableFloatStateOf(0f) }
    val report by rememberUpdatedState(onValue)
    val shape = RoundedCornerShape(24.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.35f))
            .border(1.dp, Color.White.copy(alpha = 0.35f), shape)
            .pointerInput(vertical) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val travel = (if (vertical) size.height else size.width) * TRAVEL
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val delta = if (vertical) {
                                down.position.y - change.position.y
                            } else {
                                change.position.x - down.position.x
                            }
                            val updated = Axis.deadzone((delta / travel).coerceIn(-1f, 1f))
                            if (updated != value) {
                                value = updated
                                report(updated)
                            }
                            change.consume()
                        }
                    } finally {
                        // Al soltar o si el gesto se cancela: neutro.
                        value = 0f
                        report(0f)
                    }
                }
            }
            .drawBehind {
                val travel = (if (vertical) size.height else size.width) * TRAVEL
                val middle = center
                val (start, end, thumb) = if (vertical) {
                    Triple(
                        Offset(middle.x, middle.y - travel),
                        Offset(middle.x, middle.y + travel),
                        Offset(middle.x, middle.y - value * travel),
                    )
                } else {
                    Triple(
                        Offset(middle.x - travel, middle.y),
                        Offset(middle.x + travel, middle.y),
                        Offset(middle.x + value * travel, middle.y),
                    )
                }
                drawLine(
                    color = Color.White.copy(alpha = 0.3f),
                    start = start,
                    end = end,
                    strokeWidth = 6.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawCircle(color = Accent, radius = 22.dp.toPx(), center = thumb)
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Botón de acción semitransparente. [active] lo resalta (luces o humo encendidos). */
@Composable
fun ActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 64.dp, minHeight = 48.dp)
            .clip(shape)
            .background(if (active) Accent else Color.Black.copy(alpha = 0.5f))
            .border(1.dp, if (active) Accent else Color.White.copy(alpha = 0.4f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (active) Color.Black else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}
