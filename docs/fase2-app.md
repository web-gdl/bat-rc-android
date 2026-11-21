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
- **Sin código nativo:** compatible con cualquier ABI y con dispositivos de páginas de 16 KB.
- **Seguridad:** al salir de la app se envía durante 350 ms la orden de parar el motor, centrar la dirección y apagar el humo.
- **Sin material de la app original:** ni gráficos ni sonidos; icono e interfaz propios.

## Verificación

- El codificador de comandos se prueba en `CommandEncoderTest` contra una transcripción literal de `AndroidMain.getSendCmd()` (20 000 pasos aleatorios y casos fijos). Los tests corren en cada compilación de GitHub Actions.
- La app no se ha probado todavía con un vehículo real.
