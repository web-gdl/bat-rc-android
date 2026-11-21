package org.batrc.controller.protocol

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Random

class CommandEncoderTest {

    @Test
    fun idlePacketAndHeartbeat() {
        val encoder = CommandEncoder()
        val packets = List(20) { encoder.next() }
        packets.forEachIndexed { index, packet ->
            val beat = if ((index + 1) % 10 == 0) "01" else "00"
            assertEquals("41_80_31_80_50_F0_00_00_$beat", packet)
        }
    }

    @Test
    fun singleCommandIsPadded() {
        val encoder = CommandEncoder()
        encoder.setHeadlights(true)
        assertEquals("A1_10_50_F0_00_00_00_00_00", encoder.next())
        assertEquals("41_80_31_80_50_F0_00_00_00", encoder.next())
    }

    @Test
    fun axesUseUnpaddedLowercaseHex() {
        val encoder = CommandEncoder()
        encoder.setThrottle(150) // adelante 50 -> 0x80 + 50 = 0xb2
        encoder.setSteering(95)  // 5 hacia el otro lado -> "5"
        assertEquals("41_b2_31_5_50_F0_00_00_00", encoder.next())
        encoder.setThrottle(200)
        encoder.setSteering(0)
        assertEquals("41_e4_31_64_50_F0_00_00_00", encoder.next())
    }

    @Test
    fun overflowWaitsForNextPacketInPriorityOrder() {
        val encoder = CommandEncoder()
        encoder.setThrottle(150)
        encoder.setSteering(150)
        encoder.setHeadlights(true)
        encoder.setSmoke(true)
        encoder.playSound(Protocol.SOUND_CANNON)
        assertEquals("41_b2_31_b2_A1_10_50_F0_00", encoder.next())
        assertEquals("41_b2_31_b2_91_10_50_F0_00", encoder.next())
        assertEquals("41_b2_31_b2_01_65_50_F0_00", encoder.next())
        assertEquals("41_b2_31_b2_50_F0_00_00_00", encoder.next())
    }

    @Test
    fun matchesOriginalImplementationOnRandomInput() {
        val random = Random(1051)
        val encoder = CommandEncoder()
        val original = OriginalSendCmd()
        repeat(20_000) { step ->
            when (random.nextInt(14)) {
                0, 1 -> random.nextInt(201).let { encoder.setThrottle(it); original.setForwadBack(it) }
                2, 3 -> random.nextInt(201).let { encoder.setSteering(it); original.setLeftRight(it) }
                4 -> { encoder.stopThrottle(); original.setForwadBackStopTouch() }
                5 -> { encoder.centerSteering(); original.setLeftRightStopTouch() }
                6 -> random.nextBoolean().let { encoder.setHeadlights(it); original.setHeadLED(it) }
                7 -> random.nextBoolean().let { encoder.setSmoke(it); original.setSmokeGenerator(it) }
                8 -> { encoder.toggleBodyShape(); original.setBodyShapeOn() }
                9 -> { encoder.toggleGunUpDown(); original.setGunUpDownOn() }
                10 -> { encoder.rotateGun(); original.setGunRotationOn() }
                11 -> (1 + random.nextInt(110)).let { encoder.playSound(it); original.setVoice(it) }
                12 -> (random.nextInt(3) - 1).let { encoder.calibrate(it); original.setCalibration(it) }
                else -> Unit
            }
            if (random.nextInt(3) != 0) {
                assertEquals("paso $step", original.getSendCmd(), encoder.next())
            }
        }
    }
}

/**
 * Transcripción literal del código decompilado de `android.unity.AndroidMain`
 * (v1.3.1). Sirve de referencia independiente para comprobar [CommandEncoder].
 */
internal class OriginalSendCmd {
    private var strFB = ""
    private var strRL = ""
    private var strLED = ""
    private var strSMOKE = ""
    private var strSHAPE = ""
    private var strUPDOWN = ""
    private var strROTATE = ""
    private var strVOICE = ""
    private var strADJ = ""
    private var strCalibration = ""
    private var tagRotate = false
    private var receiveCount = 0

    fun setForwadBack(p: Int) {
        if (p >= 100) {
            if (p != 100) {
                strFB = "41_" + Integer.toHexString((p - 100) + 128)
            } else {
                strFB = ""
            }
        } else {
            strFB = "41_" + Integer.toHexString((p - 100) * -1)
        }
    }

    fun setLeftRight(p: Int) {
        if (p >= 100) {
            if (p != 100) {
                strRL = "31_" + Integer.toHexString((p - 100) + 128)
            } else {
                strRL = ""
            }
        } else {
            strRL = "31_" + Integer.toHexString((p - 100) * -1)
        }
    }

    fun setForwadBackStopTouch() { strFB = "41_80" }
    fun setLeftRightStopTouch() { strRL = "31_80" }
    fun setHeadLED(on: Boolean) { strLED = if (!on) "A1_01" else "A1_10" }
    fun setSmokeGenerator(on: Boolean) { strSMOKE = if (!on) "91_01" else "91_10" }
    fun setBodyShapeOn() { strSHAPE = "21_64" }
    fun setGunUpDownOn() { strUPDOWN = "21_E4" }
    fun setGunRotationOn() { tagRotate = true }
    fun setVoice(p: Int) { strVOICE = "01_" + Integer.toHexString(p - 1) }

    fun setCalibration(p: Int) {
        if (p != 0) {
            if (p != 1) {
                if (p == -1) strCalibration = "B1_01"
            } else {
                strCalibration = "B1_81"
            }
        } else {
            strCalibration = "B1_00"
        }
    }

    fun getSendCmd(): String {
        var cmd = ""
        var count = 0
        if (strFB != "") {
            cmd = "" + strFB
            count = 1
        }
        if (strRL != "" && count < 3) {
            if (cmd != "") cmd += "_"
            cmd += strRL
            count++
        }
        if (strCalibration != "" && count < 3) {
            if (cmd != "") cmd += "_"
            cmd += strCalibration
            count++
            strCalibration = ""
        }
        if (strLED != "" && count < 3) {
            if (cmd != "") cmd += "_"
            cmd += strLED
            count++
            strLED = ""
        }
        if (strSMOKE != "" && count < 3) {
            if (cmd != "") cmd += "_"
            cmd += strSMOKE
            count++
            strSMOKE = ""
        }
        if (strSHAPE != "" && count < 3) {
            if (cmd != "") cmd += "_"
            cmd += strSHAPE
            count++
            strSHAPE = ""
        }
        if (strUPDOWN != "" && count < 3) {
            if (cmd != "") cmd += "_"
            cmd += strUPDOWN
            count++
            strUPDOWN = ""
        }
        if (strVOICE != "" && count < 3) {
            if (cmd != "") cmd += "_"
            cmd += strVOICE
            count++
            strVOICE = ""
        }
        if (tagRotate && count < 3) {
            strROTATE = "11_E4"
            if (cmd != "") cmd += "_"
            cmd += strROTATE
            count++
            tagRotate = false
        }
        if (strADJ != "" && count < 3) {
            if (cmd != "") cmd += "_"
            cmd += strADJ
            count++
            strADJ = ""
        }
        if (cmd != "") cmd += "_"

        receiveCount = (receiveCount + 1) % 10
        return if (count <= 0) {
            if (receiveCount != 0) "41_80_31_80_50_F0_00_00_00" else "41_80_31_80_50_F0_00_00_01"
        } else {
            var out = cmd + "50_F0"
            val n = count + 1
            var i = 0
            while (i < 4 - n) {
                out += "_00_00"
                i++
            }
            if (receiveCount != 0) out + "_00" else out + "_01"
        }
    }
}
