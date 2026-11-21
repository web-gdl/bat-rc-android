package org.batrc.controller.protocol

/**
 * Constantes del protocolo, extraídas de `android.unity.AndroidMain`
 * (classes.dex) y de `libil2cpp.so` de la app original v1.3.1.
 */
object Protocol {
    /** IP fija del vehículo dentro de su propio punto de acceso Wi-Fi. */
    const val HOST = "192.168.201.1"

    /** Puerto UDP de control. */
    const val CONTROL_PORT = 1051

    /** La app original envía un datagrama cada 100 ms. */
    const val PERIOD_MS = 100L

    const val RTSP_URL = "rtsp://$HOST/live/ch00_0"
    const val HTTP_BASE = "http://$HOST"

    const val CGI_AUTO_WHITE_BALANCE = "/isp/awb_ctl.cgi?ENABLE=1"
    const val CGI_DEVICE_INFO = "/custom/getDeviceInformation.cgi"

    // Números de sonido que usa la app original (Main.FireMissle / Main.FireCannon
    // y el contador de Manager.RevEngine).
    const val SOUND_ENGINE_FIRST = 1
    const val SOUND_ENGINE_LAST = 3
    const val SOUND_MISSILE = 101
    const val SOUND_CANNON = 102

    /** Sonidos del menú de pruebas oculto de la app original (SoundMenu.Play100..103). */
    val TEST_SOUNDS = listOf(100, 101, 102, 103)
}
