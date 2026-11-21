package org.batrc.controller.fx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FxControllerTest {

    private fun FxController.run(from: Double, to: Double) {
        var t = from
        while (t <= to) {
            step(t)
            t += 1.0 / 60
        }
    }

    @Test
    fun missileExplodesAfterTwoSeconds() {
        val fx = FxController()
        val events = mutableListOf<FxEvent>()
        fx.listener = { events += it }
        fx.fireMissile(0.0)
        fx.run(0.0, 1.95)
        assertTrue(fx.effects.none { it is FxController.Explosion })
        fx.run(1.96, 2.05)
        assertTrue(fx.effects.any { it is FxController.Explosion })
        assertEquals(listOf(FxEvent.ROCKET_IMPACT), events)
    }

    @Test
    fun missilesAlternateLaunchers() {
        val fx = FxController()
        fx.fireMissile(0.0)
        fx.fireMissile(0.1)
        val rockets = fx.effects.filterIsInstance<FxController.Rocket>()
        assertEquals(FxController.LAUNCHER_LEFT, rockets[0].from)
        assertEquals(FxController.LAUNCHER_RIGHT, rockets[1].from)
    }

    @Test
    fun cannonFlashPositionFollowsCannonState() {
        val fx = FxController()
        fx.fireCannon(0.0)
        fx.cannonUp = true
        fx.fireCannon(0.1)
        val flashes = fx.effects.filterIsInstance<FxController.CannonFlash>()
        assertEquals(FxController.CANNON_DOWN, flashes[0].at)
        assertEquals(FxController.CANNON_UP, flashes[1].at)
    }

    @Test
    fun crosshairFollowsSticksWithinOriginalRange() {
        val fx = FxController()
        fx.setAim(1f, 1f)
        fx.run(0.0, 2.0)
        assertEquals(FxController.CENTER.x + FxController.AIM_RANGE_X, fx.crosshair.x, 0.002f)
        assertEquals(FxController.CENTER.y - FxController.AIM_RANGE_Y, fx.crosshair.y, 0.002f)
        fx.setAim(0f, 0f)
        fx.run(2.0, 4.0)
        assertEquals(FxController.CENTER.x, fx.crosshair.x, 0.002f)
    }

    @Test
    fun machineGunDestroysLockedTarget() {
        val fx = FxController()
        fx.targetMode = true
        fx.run(0.0, 1.0)
        val target = fx.targets.first()
        fx.setAim(
            (target.at.x - FxController.CENTER.x) / FxController.AIM_RANGE_X,
            (FxController.CENTER.y - target.at.y) / FxController.AIM_RANGE_Y,
        )
        fx.run(1.0, 1.5)
        assertEquals(target, fx.lockedTarget)
        fx.startGun(1.5)
        fx.run(1.5, 2.5)
        assertTrue(!target.alive)
        assertEquals(1, fx.score)
    }
}
