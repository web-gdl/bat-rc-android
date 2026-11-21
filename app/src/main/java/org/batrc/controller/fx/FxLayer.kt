package org.batrc.controller.fx

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableDoubleState
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private val Fire = Color(0xFFFFB13B)
private val FireDeep = Color(0xFFFF5A1F)
private val FireCore = Color(0xFFFFF4C8)
private val Smoke = Color(0xFF3A3A3A)
private val Trail = Color(0xFFB8B8B8)
private val Hud = Color(0xFFFFFFFF)
private val HudLocked = Color(0xFFFF3B30)

/**
 * Reloj de los efectos: avanza [controller] en cada fotograma.
 * Devuelve el tiempo actual en segundos. Si [frozenAt] no es nulo, el tiempo
 * queda fijo en ese valor (se usa para generar capturas).
 */
@Composable
fun rememberFxClock(controller: FxController, frozenAt: Double? = null): MutableDoubleState {
    val clock = remember { mutableDoubleStateOf(0.0) }
    LaunchedEffect(controller, frozenAt) {
        if (frozenAt != null) {
            controller.step(frozenAt)
            clock.doubleValue = frozenAt
            return@LaunchedEffect
        }
        while (true) {
            withFrameNanos { nanos ->
                val now = nanos / 1_000_000_000.0
                controller.step(now)
                clock.doubleValue = now
            }
        }
    }
    return clock
}

/** Capa de efectos dibujada encima del vídeo. */
@Composable
fun FxCanvas(
    controller: FxController,
    time: () -> Double,
    showCrosshair: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        // El tiempo se lee aquí, en la fase de dibujo: cada fotograma solo
        // vuelve a dibujar esta capa, sin recomponer la pantalla.
        val now = time()
        drawTargets(controller, now)
        for (effect in controller.effects) {
            when (effect) {
                is FxController.Rocket -> drawRocket(effect, now)
                else -> Unit
            }
        }
        for (effect in controller.effects) {
            when (effect) {
                is FxController.Explosion -> drawExplosion(effect, now)
                is FxController.CannonFlash -> drawCannon(effect, now)
                else -> Unit
            }
        }
        if (controller.gunFiring) drawMachineGun(controller, now)
        if (showCrosshair || controller.targetMode) drawCrosshair(controller)
        drawCannonScreenFlash(controller, now)
    }
}

private fun DrawScope.at(p: P) = Offset(p.x * size.width, p.y * size.height)

/** Unidad de tamaño: 1 % de la dimensión menor de la pantalla. */
private fun DrawScope.u() = min(size.width, size.height) / 100f

// --- Ametralladora --------------------------------------------------------

private fun DrawScope.drawMachineGun(fx: FxController, now: Double) {
    val elapsed = now - fx.gunStart
    // Bucle por fotogramas, como `MachineGunFX` (hoja de sprites en bucle).
    val frame = floor(elapsed / 0.05).toInt()
    val target = at(fx.lockedTarget?.at ?: fx.crosshair)
    FxController.GUN_MUZZLES.forEachIndexed { index, muzzlePoint ->
        val muzzle = at(muzzlePoint)
        val rnd = Random(frame * 7 + index)
        // Trazadoras hacia la mira.
        val tracerT = ((elapsed * 9 + index * 0.5) % 1.0).toFloat()
        val head = Offset(muzzle.x + (target.x - muzzle.x) * tracerT, muzzle.y + (target.y - muzzle.y) * tracerT)
        val tail = Offset(
            muzzle.x + (target.x - muzzle.x) * (tracerT - 0.12f).coerceAtLeast(0f),
            muzzle.y + (target.y - muzzle.y) * (tracerT - 0.12f).coerceAtLeast(0f),
        )
        drawLine(Fire.copy(alpha = 0.9f), tail, head, strokeWidth = u() * 0.6f, cap = StrokeCap.Round)
        // Fogonazo: estrella irregular que cambia en cada fotograma.
        if ((frame + index) % 3 != 2) {
            val radius = u() * (8f + rnd.nextFloat() * 5f)
            drawStar(muzzle, radius, radius * 0.35f, 7, rnd.nextFloat() * 360f, Fire)
            drawStar(muzzle, radius * 0.55f, radius * 0.2f, 7, rnd.nextFloat() * 360f, FireCore)
            drawCircle(
                Brush.radialGradient(listOf(Fire.copy(alpha = 0.5f), Color.Transparent), muzzle, radius * 2.2f),
                radius * 2.2f,
                muzzle,
            )
        }
    }
    // Chispas de impacto en la mira.
    val rnd = Random(frame)
    repeat(6) {
        val angle = rnd.nextFloat() * 2 * PI
        val length = u() * (1.5f + rnd.nextFloat() * 3f)
        val start = Offset(target.x + cos(angle).toFloat() * u(), target.y + sin(angle).toFloat() * u())
        val end = Offset(start.x + cos(angle).toFloat() * length, start.y + sin(angle).toFloat() * length)
        drawLine(FireCore, start, end, strokeWidth = u() * 0.35f, cap = StrokeCap.Round)
    }
}

// --- Cañón ----------------------------------------------------------------

private fun DrawScope.drawCannon(flash: FxController.CannonFlash, now: Double) {
    val elapsed = now - flash.start
    // Animación por fotogramas de 0,08 s, como `CannonFX.Update`.
    val frame = floor(elapsed / FxController.CANNON_FRAME).toInt().coerceIn(0, FxController.CANNON_FRAMES - 1)
    val progress = frame / (FxController.CANNON_FRAMES - 1f)
    val center = at(flash.at)
    val rnd = Random(frame + 31)

    // Bola de fuego que crece y se apaga.
    val fireball = u() * (6f + 16f * progress)
    val fireAlpha = (1f - progress).coerceIn(0f, 1f)
    if (fireAlpha > 0f) {
        drawCircle(
            Brush.radialGradient(
                listOf(FireCore.copy(alpha = fireAlpha), Fire.copy(alpha = fireAlpha * 0.9f), FireDeep.copy(alpha = 0f)),
                center,
                fireball,
            ),
            fireball,
            center,
        )
        drawStar(center, fireball * 1.25f, fireball * 0.45f, 9, rnd.nextFloat() * 360f, Fire.copy(alpha = fireAlpha * 0.8f))
    }
    // Anillo de onda expansiva.
    val ring = u() * (8f + 30f * progress)
    drawCircle(Hud.copy(alpha = 0.45f * (1f - progress)), ring, center, style = Stroke(u() * 0.8f))
    // Humo que queda al final.
    val smokeAlpha = 0.5f * progress * (1f - progress) * 4f
    repeat(5) { i ->
        val angle = i * 72.0 + rnd.nextFloat() * 30
        val distance = ring * 0.55f
        val puff = Offset(
            center.x + cos(angle * PI / 180).toFloat() * distance,
            center.y + sin(angle * PI / 180).toFloat() * distance,
        )
        drawCircle(Smoke.copy(alpha = smokeAlpha.coerceIn(0f, 0.5f)), u() * (4f + 4f * progress), puff)
    }
}

private fun DrawScope.drawCannonScreenFlash(fx: FxController, now: Double) {
    val latest = fx.effects.filterIsInstance<FxController.CannonFlash>().maxByOrNull { it.start } ?: return
    val t = (now - latest.start).toFloat()
    if (t in 0f..0.15f) drawRect(Color.White.copy(alpha = 0.28f * (1f - t / 0.15f)))
}

// --- Misiles ---------------------------------------------------------------

private fun DrawScope.drawRocket(rocket: FxController.Rocket, now: Double) {
    val elapsed = now - rocket.start
    // Estela: posiciones anteriores, cada vez más tenues (sistema de partículas de `RocketFX`).
    val steps = 26
    for (i in steps downTo 1) {
        val past = now - i * 0.035
        if (past < rocket.start) continue
        val p = at(rocket.position(past))
        val fade = 1f - i / steps.toFloat()
        val wobble = Random(i * 13 + rocket.start.hashCode())
        val drift = Offset((wobble.nextFloat() - 0.5f) * u() * 2f * (1 - fade), -u() * 1.5f * (1 - fade))
        // Humo claro y, junto a la cabeza, llama.
        drawCircle(Trail.copy(alpha = 0.55f * fade), u() * (1.2f + 3.5f * (1 - fade)), p + drift)
        if (i <= 4) drawCircle(Fire.copy(alpha = 0.8f * (1f - i / 5f)), u() * (2.2f - i * 0.3f), p)
    }
    val head = at(rocket.position(now))
    // Se aleja: más pequeño cuanto más avanza.
    val scale = 1f - 0.6f * (elapsed / rocket.duration).toFloat().coerceIn(0f, 1f)
    drawCircle(
        Brush.radialGradient(listOf(FireCore, Fire, Color.Transparent), head, u() * 4f * scale),
        u() * 4f * scale,
        head,
    )
    drawCircle(FireCore, u() * 1.1f * scale, head)
}

// --- Explosiones -----------------------------------------------------------

private fun DrawScope.drawExplosion(explosion: FxController.Explosion, now: Double) {
    val t = ((now - explosion.start) / explosion.duration).toFloat().coerceIn(0f, 1f)
    val center = at(explosion.at)
    val s = explosion.size * u()
    val rnd = Random(explosion.seed)

    // Destello inicial.
    if (t < 0.25f) {
        val k = 1f - t / 0.25f
        drawCircle(
            Brush.radialGradient(listOf(FireCore.copy(alpha = k), Fire.copy(alpha = 0.7f * k), Color.Transparent), center, s * 18f),
            s * 18f,
            center,
        )
    }
    // Bolas de fuego.
    repeat(7) {
        val angle = rnd.nextFloat() * 2 * PI
        val distance = s * (2f + rnd.nextFloat() * 6f) * (0.4f + t)
        val radius = s * (3f + rnd.nextFloat() * 4f) * (1f - t * 0.6f)
        val p = Offset(center.x + cos(angle).toFloat() * distance, center.y + sin(angle).toFloat() * distance - s * 6f * t)
        val alpha = (1f - t * 1.6f).coerceIn(0f, 1f)
        if (alpha > 0f) drawCircle(if (it % 2 == 0) Fire.copy(alpha = alpha) else FireDeep.copy(alpha = alpha), radius, p)
    }
    // Humo que sube.
    repeat(6) {
        val angle = rnd.nextFloat() * 2 * PI
        val p = Offset(
            center.x + cos(angle).toFloat() * s * 5f * t,
            center.y + sin(angle).toFloat() * s * 3f * t - s * 10f * t,
        )
        drawCircle(Smoke.copy(alpha = 0.55f * t * (1f - t) * 4f / 1f), s * (4f + 6f * t), p)
    }
    // Escombros.
    repeat(10) {
        val angle = rnd.nextFloat() * 2 * PI
        val speed = s * (10f + rnd.nextFloat() * 18f)
        val p = Offset(
            center.x + cos(angle).toFloat() * speed * t,
            center.y + sin(angle).toFloat() * speed * t + s * 20f * t * t,
        )
        drawCircle(FireCore.copy(alpha = (1f - t).coerceIn(0f, 1f)), s * 0.5f, p)
    }
}

// --- Mira y blancos ---------------------------------------------------------

private fun DrawScope.drawCrosshair(fx: FxController) {
    val c = at(fx.crosshair)
    // Blanca sin objetivo y roja sobre un blanco (`Crosshair.NoTarget/OnTarget`).
    val color = if (fx.lockedTarget != null) HudLocked else Hud
    val r = u() * 5f
    val stroke = u() * 0.45f
    drawCircle(Color.Black.copy(alpha = 0.35f), r, c, style = Stroke(stroke * 3f))
    drawCircle(color, r, c, style = Stroke(stroke))
    for (k in 0 until 4) {
        rotate(k * 90f, c) {
            drawLine(color, Offset(c.x, c.y - r * 1.9f), Offset(c.x, c.y - r * 0.55f), strokeWidth = stroke, cap = StrokeCap.Round)
        }
    }
    drawCircle(color, stroke, c)
}

private fun DrawScope.drawTargets(fx: FxController, now: Double) {
    for (target in fx.targets) {
        if (!target.alive) continue
        val c = at(target.at)
        val appear = ((now - target.bornAt) / 0.3).toFloat().coerceIn(0f, 1f)
        val r = u() * 6f * appear
        val hit = now - target.lastHit < 0.08
        val body = if (hit) Color.White else Color(0xFFE9E9E9)
        // Dron: cuerpo con anillo exterior giratorio.
        drawCircle(Color.Black.copy(alpha = 0.45f), r * 1.15f, c)
        drawCircle(body, r * 0.55f, c)
        drawCircle(HudLocked, r * 0.28f, c)
        rotate(((now * 90) % 360).toFloat(), c) {
            drawArc(body, 0f, 70f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(u() * 0.7f))
            drawArc(body, 120f, 70f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(u() * 0.7f))
            drawArc(body, 240f, 70f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(u() * 0.7f))
        }
        // Barra de vida.
        val w = r * 2f
        val top = c.y - r * 1.6f
        drawLine(Color.Black.copy(alpha = 0.6f), Offset(c.x - w / 2, top), Offset(c.x + w / 2, top), strokeWidth = u() * 0.8f)
        val life = target.hp / FxController.TARGET_HP.toFloat()
        drawLine(Color(0xFF5BE37D), Offset(c.x - w / 2, top), Offset(c.x - w / 2 + w * life, top), strokeWidth = u() * 0.8f)
    }
}

private fun DrawScope.drawStar(center: Offset, outer: Float, inner: Float, points: Int, rotation: Float, color: Color) {
    val path = Path()
    val total = points * 2
    for (i in 0 until total) {
        val radius = if (i % 2 == 0) outer else inner
        val angle = (i * PI / points) + rotation * PI / 180
        val x = center.x + cos(angle).toFloat() * radius
        val y = center.y + sin(angle).toFloat() * radius
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
}
