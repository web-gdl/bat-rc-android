# Fase 2 — App nativa «BatRC Control»

Proyecto Android (Kotlin + Jetpack Compose). Paquete `org.batrc.controller`, minSdk 23, targetSdk/compileSdk 35.

## Incógnitas de la fase 1 resueltas

Desensamblado de `libil2cpp.so` (arm64) de la app original.

- **Ejes:** `Main.SetForwadBack(v)` y `Main.SetLeftRight(v)` reciben v en −1..1 y envían `truncar(v × 100) + 100` (0..200).
- **Curva del acelerador** (`Manager.UpdateThrottle`): v < 0,75 → v × 0,6666; si no → (v − 0,75) × 2 + 0,5. La dirección va sin curva.
- **Sentido:** un valor positivo produce el rango `0x81`–`0xE4`. Que positivo sea «adelante» es una inferencia (la simulación interna avanza con valor positivo); falta confirmarlo con el vehículo. La app tiene un ajuste para invertirlo.
- **Armas** (`Manager.WeaponActivated(tipo)`): 0 = ametralladora → `SetGunRotationOn` (`11_E4`); 1 = cañón → `SetVoice(102)` (`01_65`); 2 = misiles → `SetVoice(101)` (`01_64`).
- **Motor** (`Manager.RevEngine`): `SetVoice(n)` con un contador que recorre 1, 2 y 3.
- **Menú de pruebas oculto** (`SoundMenu`): sonidos 100, 101, 102 y 103.
- Al pausar o salir, la app original apaga el humo (`SetSmokeGenerator(false)`).

## Sin resolver

- Códec del vídeo RTSP (se asume H.264; Media3 no reproduce MJPEG sobre RTP).
- Qué suena con los sonidos 100 y 103.
- Formato del parámetro `value` al cambiar la contraseña del Wi-Fi (no implementado en la app).

## Decisiones de diseño

- **Sin permisos peligrosos:** ni ubicación ni `NEARBY_WIFI_DEVICES`. La red del vehículo se reconoce por la IP 192.168.201.x leída de `LinkProperties`, no por el nombre (SSID), que es lo que exigiría esos permisos.
- **Enlace de red:** `ConnectivityManager.requestNetwork` (Wi-Fi, sin exigir internet) + `bindProcessToNetwork`. Además el socket UDP y las conexiones RTSP/HTTP se atan explícitamente a esa red.
- **Vídeo:** Media3 ExoPlayer con su módulo RTSP, búfer mínimo, reintento automático y opción de forzar TCP.
- **Sin código nativo propio.** La única librería nativa es `libandroidx.graphics.path.so` de Compose, incluida para arm64-v8a, armeabi-v7a, x86 y x86_64 y alineada a 16 KB (comprobado en el APK publicado).
- **Seguridad:** al salir de la app se envía durante 350 ms la orden de parar el motor, centrar la dirección y apagar el humo.
- **Sin material de la app original:** ni gráficos ni sonidos; icono e interfaz propios.

## Efectos al disparar (versión 1.1)

Comportamiento extraído de `libil2cpp.so` de la app original:

- `Batman.Batcar.Buttons.Weapon`: los botones de armas actúan al pulsar y al soltar (`OnPointerDown` / `OnPointerUp`). La ametralladora envía un solo pulso `11_E4` al pulsar; `SetGunRotationOFF` al soltar no cambia nada en el protocolo.
- `Batman.FX.MachineGunFX`: hoja de sprites en bucle mientras está activa.
- `Batman.FX.CannonFX`: animación por fotogramas con un temporizador de 0,08 s; su posición cambia con el estado del cañón (`ShootCannon(isDown)`) y la relación de aspecto (16:9 o 4:3).
- `Batman.FX.RocketFX`: sistema de partículas que se desplaza en línea recta (`Vector3.Lerp`) durante `lifeTime` = 2,0 s; `RocketFX_Controller` alterna entre varios lanzadores.
- `FXController.*Vibrate`: vibración al disparar; con la ametralladora, `Handheld.Vibrate` cada 1,0 s mientras dispara. Cañón y misiles esperan 0,5 s en su corrutina.
- `Buttons.Weapon._camShake`: sacudida de cámara.
- Modo de juego (`Batman.Game.*`): blancos con vida (`Target._HP`) y explosión; la mira (`Crosshair`) se mueve con los mandos dentro de ±14 (horizontal) y ±4 (vertical) a distancia 25, se pone roja sobre un blanco (`OnTarget`) y se acerca sola a él (`AutoTarget`, `Lerp` 10 × dt).

No recuperable: las imágenes, las animaciones y los sonidos (estaban en el OBB), y los valores ajustados en las escenas (vida de los blancos, cadencias, intensidad de la sacudida). Esos valores y todos los gráficos y sonidos de esta app son propios.

## Verificación

- El codificador de comandos se prueba en `CommandEncoderTest` contra una transcripción literal de `AndroidMain.getSendCmd()` (20 000 pasos aleatorios y casos fijos). Los tests corren en cada compilación de GitHub Actions.
- `FxControllerTest` comprueba los tiempos y el comportamiento de los efectos.
- Cada compilación instala la app en un emulador de Android 14 y guarda capturas reales en la rama `capturas`.
- La app no se ha probado todavía con un vehículo real.
