package org.batrc.controller.fx

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.random.Random

/** Punto en coordenadas normalizadas de la pantalla: x e y van de 0 a 1 (y hacia abajo). */
data class P(val x: Float, val y: Float) {
    fun lerp(to: P, t: Float) = P(x + (to.x - x) * t, y + (to.y - y) * t)
    fun distance(other: P) = hypot(x - other.x, y - other.y)
}

/** Sucesos que producen sonido o vibración fuera de la capa visual. */
enum class FxEvent { ROCKET_IMPACT, TARGET_DESTROYED }

/**
 * Estado y lógica de los efectos de disparo, independiente de Android.
 *
 * Reproduce el comportamiento de la app original (clases `Batman.FX.*`,
 * `Batman.Game.*` y `FXController` de libil2cpp.so):
 *
 * - Ametralladora: fogonazo en bucle mientras se mantiene pulsado.
 * - Cañón: animación por fotogramas de 0,08 s; su posición depende de si el
 *   cañón está arriba o abajo.
 * - Misiles: cada pulsación lanza uno, alternando lanzador, que viaja en línea
 *   recta durante 2,0 s hasta el punto de mira y explota.
 * - Sacudida de la imagen al disparar.
 * - Modo objetivos: blancos virtuales sobre el vídeo. La mira se mueve con los
 *   mandos (±14 en horizontal y ±4 en vertical a distancia 25 en el original),
 *   se vuelve roja sobre un blanco y se ajusta sola hacia él.
 *
 * El tiempo se pasa siempre como parámetro (segundos) para que el estado sea
 * reproducible y se pueda dibujar cualquier instante.
 */
class FxController(seed: Int = 1051) {

    sealed interface Effect {
        val start: Double
        val duration: Double
    }

    data class CannonFlash(override val start: Double, val at: P) : Effect {
        override val duration = CANNON_FRAME * CANNON_FRAMES
    }

    data class Rocket(override val start: Double, val from: P, val to: P, val target: Target?) : Effect {
        override val duration = ROCKET_LIFE
        fun position(now: Double): P = from.lerp(to, ((now - start) / duration).toFloat().coerceIn(0f, 1f))
    }

    data class Explosion(override val start: Double, val at: P, val size: Float, val seed: Int) : Effect {
        override val duration = 1.3
    }

    data class Shake(override val start: Double, override val duration: Double, val amplitude: Float) : Effect

    class Target(val id: Int, val at: P, val bornAt: Double) {
        var hp = TARGET_HP
        var lastHit = -10.0
        var diedAt = -1.0
        val alive get() = diedAt < 0
    }

    private val random = Random(seed)
    private var nextTargetId = 0
    private var nextLauncher = 0
    private var lastStep = 0.0
    private var lastGunTick = 0.0
    private var aimX = 0f
    private var aimY = 0f

    val effects = ArrayList<Effect>()
    val targets = ArrayList<Target>()

    /** Se llama al impactar un misil o destruir un blanco (para sonido y vibración). */
    var listener: ((FxEvent) -> Unit)? = null

    var gunFiring = false
        private set
    var gunStart = 0.0
        private set

    /** Estado del cañón, que cambia con el botón «Cañón ↕». */
    var cannonUp = false

    var crosshair = CENTER
        private set
    var lockedTarget: Target? = null
        private set

    var targetMode = false
        set(value) {
            if (field == value) return
            field = value
            if (!value) {
                targets.clear()
                lockedTarget = null
            }
        }

    var score = 0
        private set

    /** Entrada de los mandos, -1..1. Mueve la mira como `Crosshair.LeftRight/UpDown`. */
    fun setAim(steering: Float, throttle: Float) {
        aimX = steering.coerceIn(-1f, 1f)
        aimY = throttle.coerceIn(-1f, 1f)
    }

    fun startGun(now: Double) {
        if (gunFiring) return
        gunFiring = true
        gunStart = now
        lastGunTick = now
    }

    fun stopGun() {
        gunFiring = false
    }

    fun fireCannon(now: Double) {
        effects += CannonFlash(now, if (cannonUp) CANNON_UP else CANNON_DOWN)
        effects += Shake(now, 0.35, 1f)
        val victim = lockedTarget
        if (targetMode && victim != null) damage(victim, CANNON_DAMAGE, now)
    }

    fun fireMissile(now: Double) {
        val from = if (nextLauncher % 2 == 0) LAUNCHER_LEFT else LAUNCHER_RIGHT
        nextLauncher++
        val target = if (targetMode) lockedTarget else null
        effects += Rocket(now, from, target?.at ?: crosshair, target)
        effects += Shake(now, 0.2, 0.35f)
    }

    /** Avanza la simulación hasta [now]. */
    fun step(now: Double) {
        val dt = (now - lastStep).coerceIn(0.0, 0.1)
        lastStep = now

        // Misiles que llegan: explosión (y daño en modo objetivos).
        val arrived = effects.filterIsInstance<Rocket>().filter { now - it.start >= it.duration }
        for (rocket in arrived) {
            effects += Explosion(now, rocket.to, 1f, random.nextInt())
            effects += Shake(now, 0.3, 0.7f)
            rocket.target?.let { if (it.alive) damage(it, ROCKET_DAMAGE, now) }
            listener?.invoke(FxEvent.ROCKET_IMPACT)
        }
        effects.removeAll { now - it.start >= it.duration }

        if (gunFiring) {
            // Vibración continua de la imagen mientras dispara.
            if (effects.none { it is Shake && it.amplitude < 0.2f && now - it.start < it.duration }) {
                effects += Shake(now, 0.12, 0.15f)
            }
            if (targetMode && now - lastGunTick >= GUN_TICK) {
                lastGunTick = now
                lockedTarget?.let { damage(it, GUN_DAMAGE, now) }
            }
        }

        stepCrosshair(dt.toFloat())
        if (targetMode) stepTargets(now)
    }

    /** Desplazamiento de la sacudida, en fracciones de la amplitud máxima (-1..1). */
    fun shake(now: Double): P {
        var amount = 0f
        for (effect in effects) {
            if (effect !is Shake) continue
            val t = ((now - effect.start) / effect.duration).toFloat()
            if (t in 0f..1f) amount = maxOf(amount, effect.amplitude * (1f - t))
        }
        if (amount == 0f) return P(0f, 0f)
        val phase = now * 47.0
        return P(
            (kotlin.math.sin(phase) * amount).toFloat(),
            (kotlin.math.cos(phase * 1.3) * amount).toFloat(),
        )
    }

    val isIdle: Boolean
        get() = effects.isEmpty() && !gunFiring && !targetMode &&
            abs(crosshair.x - CENTER.x) < 0.001f && abs(crosshair.y - CENTER.y) < 0.001f

    private fun stepCrosshair(dt: Float) {
        val locked = lockedTarget?.takeIf { it.alive && targetMode }
        val goal = if (locked != null) {
            // `Crosshair.AutoTarget`: se acerca al blanco (Lerp con 10 × dt).
            locked.at
        } else {
            P(CENTER.x + aimX * AIM_RANGE_X, CENTER.y - aimY * AIM_RANGE_Y)
        }
        // `Crosshair.StandardPosition`: vuelve suavemente (Lerp con 10 × dt).
        val follow = 1f - exp(-10f * dt)
        crosshair = crosshair.lerp(goal, follow)

        lockedTarget = if (targetMode) {
            val free = P(CENTER.x + aimX * AIM_RANGE_X, CENTER.y - aimY * AIM_RANGE_Y)
            targets.filter { it.alive }.minByOrNull { it.at.distance(free) }
                ?.takeIf { it.at.distance(free) < LOCK_RADIUS }
        } else {
            null
        }
    }

    private fun stepTargets(now: Double) {
        targets.removeAll { !it.alive && now - it.diedAt > TARGET_RESPAWN }
        val alive = targets.count { it.alive }
        val lastBirth = targets.maxOfOrNull { it.bornAt } ?: -10.0
        if (alive < MAX_TARGETS && now - lastBirth > SPAWN_INTERVAL) {
            targets += Target(nextTargetId++, randomPlacement(), now)
        }
    }

    /** Zona de juego: franja central, sin solaparse con otros blancos ni con los mandos. */
    private fun randomPlacement(): P {
        repeat(12) {
            val candidate = P(0.25f + random.nextFloat() * 0.5f, 0.3f + random.nextFloat() * 0.3f)
            if (targets.none { it.alive && it.at.distance(candidate) < 0.14f }) return candidate
        }
        return P(0.5f, 0.42f)
    }

    private fun damage(target: Target, amount: Int, now: Double) {
        if (!target.alive) return
        target.hp -= amount
        target.lastHit = now
        if (target.hp <= 0) {
            target.diedAt = now
            score++
            effects += Explosion(now, target.at, 1.2f, random.nextInt())
            effects += Shake(now, 0.3, 0.7f)
            if (lockedTarget == target) lockedTarget = null
            listener?.invoke(FxEvent.TARGET_DESTROYED)
        }
    }

    companion object {
        val CENTER = P(0.5f, 0.46f)

        // Posiciones en pantalla (propias: dependen de dónde se vea cada arma en la cámara).
        // Por encima de la fila de botones de armas, para que no queden tapados.
        val GUN_MUZZLES = listOf(P(0.42f, 0.74f), P(0.58f, 0.74f))
        val CANNON_UP = P(0.5f, 0.24f)
        val CANNON_DOWN = P(0.5f, 0.6f)
        val LAUNCHER_LEFT = P(0.3f, 0.8f)
        val LAUNCHER_RIGHT = P(0.7f, 0.8f)

        // Tiempos del original.
        const val CANNON_FRAME = 0.08
        const val CANNON_FRAMES = 8
        const val ROCKET_LIFE = 2.0

        // Rango de la mira: ±14 y ±4 unidades a distancia 25 con el campo de visión
        // por defecto de Unity (60°) ≈ ±27 % del ancho y ±14 % del alto.
        const val AIM_RANGE_X = 0.27f
        const val AIM_RANGE_Y = 0.14f

        // Modo objetivos (valores propios: los del original estaban en el OBB).
        const val LOCK_RADIUS = 0.09f
        const val TARGET_HP = 6
        const val GUN_DAMAGE = 1
        const val GUN_TICK = 0.1
        const val CANNON_DAMAGE = 4
        const val ROCKET_DAMAGE = 6
        const val MAX_TARGETS = 3
        const val SPAWN_INTERVAL = 0.8
        const val TARGET_RESPAWN = 1.0
    }
}
