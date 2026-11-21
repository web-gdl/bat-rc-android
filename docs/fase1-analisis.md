# Batmobile R/C Controller v1.3.1 — Fase 1: análisis de ingeniería inversa

> **Nota:** este es el informe de la primera pasada. Las incógnitas marcadas **[?]** sobre el sentido de los ejes,
> los sonidos y las armas se resolvieron después desensamblando `libil2cpp.so`; ver [fase2-app.md](fase2-app.md).

APK analizado: `BatmobileController.apk` (75,3 MB), SHA-256 `c7faecea75cc9cf6c9d743a8553d4d22cc65a3769e0dead5bd69573356239fa8`.
Método: solo análisis estático (manifest, DEX decompilado, metadatos IL2CPP, cabeceras ELF). Nada se ejecutó ni se probó contra el vehículo.

Leyenda: **[O]** observado en el binario · **[I]** inferido · **[?]** sin resolver.

## 1. Identidad y estructura

- [O] Paquete `com.mattel.batmobilerc`, versionName 1.3.1, versionCode 125.
- [O] `minSdkVersion 22`, `targetSdkVersion 30`, `compileSdkVersion 30`.
- [O] Motor Unity 2019.3.8f1 con IL2CPP (metadatos v24). Desarrollador: YETi CGI.
- [O] ABIs: `arm64-v8a` y `armeabi-v7a`. La app ya es de 64 bits.
- [O] Firma v1 + v2 (sin v3), certificado RSA de 1024 bits / SHA-1, válido 2017–2067.
- [O] Toda la lógica de red está en Java (`android.unity.AndroidMain`, `android.unity.WebServices`) y en `librtspclient.so`. El C# de Unity solo llama a esos métodos por JNI (`AndroidController`).

## 2. Diagnóstico de incompatibilidad

Lo que NO es el problema:
- [O] targetSdk 30 supera el mínimo que exigen Android 14 (23) y Android 15 (24) para instalar.
- [O] Hay librerías arm64-v8a.
- [O] No hay componentes con intent-filter sin `android:exported` que apliquen (target 30 < 31).

Causas reales o probables:
1. [O] **Falta el OBB.** El APK solo contiene la escena `level0` (cargador). Usa `GooglePlayDownloader` / `UnityDownloaderActivity` y busca `Android/obb/com.mattel.batmobilerc/main.125.com.mattel.batmobilerc.obb`. Sin ese archivo intenta descargarlo con licencia de Google Play. [I] Si la app ya no está en Play, se queda bloqueada ahí.
2. [O] **Sin enlace de red al Wi-Fi del juguete.** No hay ninguna llamada a `bindProcessToNetwork` ni `NetworkRequest`. [I] En Android moderno, un Wi-Fi sin internet no es la red por defecto y el tráfico a 192.168.201.1 sale por datos móviles.
3. [O] **Páginas de 16 KB.** `lib/arm64-v8a/librtspclient.so` está alineada a 0x1000 (4 KB); el resto de las .so arm64 a 0x10000. [I] En dispositivos con páginas de 16 KB no cargará.
4. [O] Usa `org.apache.http.legacy` (HttpClient de Apache) y HTTP en claro, sin `usesCleartextTraffic` ni network security config.
5. [?] El mensaje de error exacto al instalar no se conoce; hace falta para confirmar cuál aplica.

## 3. Protocolo de control (UDP)

Fuente: `android.unity.AndroidMain` (classes.dex).

- [O] Destino `192.168.201.1`, puerto UDP **1051**. `DatagramSocket` sin bind, `soTimeout` 100 ms.
- [O] Un datagrama cada **100 ms**.
- [O] La carga es **texto ASCII**: tokens hexadecimales separados por `_` (`strSendCmd.getBytes()`), no bytes binarios.
- [O] Estructura: hasta 3 comandos (par `ID_VALOR`) + `50_F0` + relleno `00_00` hasta 4 pares + un token final de latido. Siempre 9 tokens.
- [O] Latido: token final `01` en 1 de cada 10 paquetes, `00` en el resto.
- [O] Reposo: `41_80_31_80_50_F0_00_00_00` (o `..._01`).
- [O] Ejemplos: 1 comando → `A1_10_50_F0_00_00_00_00_00`; 3 comandos → `41_b2_31_80_A1_10_50_F0_00`.
- [O] Prioridad si hay más de 3 pendientes: avance, dirección, calibración, luces, humo, forma, cañón arriba/abajo, voz, rotación, ADJ.
- [O] Avance y dirección son persistentes (se repiten en cada paquete); los demás se envían una vez y se borran.
- [O] Los valores variables salen de `Integer.toHexString`: minúsculas y sin cero a la izquierda (`41_5`, `41_e4`). Los literales van en mayúsculas (`21_E4`).

| Función | Método Java | Comando |
|---|---|---|
| Acelerador | `SetForwadBack(v)`, v = 0..200, 100 = neutro | `41_xx`: v<100 → xx = 100−v (0x01–0x64); v>100 → xx = 0x80+(v−100) (0x81–0xE4) |
| Soltar acelerador | `SetForwadBackStopTouch()` | `41_80` |
| Dirección | `SetLeftRight(v)`, misma escala | `31_xx`, misma codificación |
| Soltar dirección | `SetLeftRightStopTouch()` | `31_80` |
| Faros | `SetHeadLED(on)` | on `A1_10`, off `A1_01` |
| Humo | `SetSmokeGenerator(on)` | on `91_10`, off `91_01` |
| Forma de carrocería (suspensión / modo blindado) | `SetBodyShapeOn()` | `21_64` (conmutador) |
| Cañón arriba/abajo | `SetGunUpDownOn()` | `21_E4` (conmutador) |
| Rotación del arma | `SetGunRotationOn()` | `11_E4` (un envío por pulsación) |
| Sonido | `SetVoice(n)` | `01_` + hex(n−1) |
| Calibración de dirección | `SetCalibration(c)` | 0 → `B1_00`, +1 → `B1_81`, −1 → `B1_01` |

- [O] Con v = 100 exacto el comando de ese eje queda vacío (no se envía parada); la parada la manda `...StopTouch`.
- [?] Qué mitad del rango es adelante/atrás e izquierda/derecha: depende del C# de Unity. *(Resuelto en la fase 2.)*
- [?] Qué número de `SetVoice` corresponde a cada sonido y qué envían `FireCannon` / `FireMissle` (C#). *(Resuelto en la fase 2.)*
- [O] `strADJ` existe pero ningún método lo rellena.
- [O] Recepción: hay un parser de 14 bytes → 7 valores de 10 bits (`Result1`..`Result7`), pero en esta versión el bucle de recepción no llama a `socket.receive`. [I] El coche no necesita respuesta del teléfono más allá del flujo de 100 ms.

## 4. Vídeo

- [O] URL: `rtsp://192.168.201.1/live/ch00_0`.
- [O] Bitmap de destino 1280×720 ARGB_8888, subido a una textura GL para Unity.
- [O] `librtspclient.so` enlaza FFmpeg n2.8.15 de forma estática; JNI: `initialWithUrl`, `decode(Bitmap)`, `stop`.
- [?] Códec (H.264 o MJPEG; el binario incluye ambos decodificadores) y transporte RTP (UDP o TCP): se determinan con `ffprobe` contra el coche.

## 5. HTTP (CGI del módulo Wi-Fi)

- [O] `GET http://192.168.201.1/isp/awb_ctl.cgi?ENABLE=1` (balance de blancos automático, al iniciar).
- [O] `GET http://192.168.201.1/custom/getDeviceInformation.cgi` (respuesta separada por comas; firmware).
- [O] `GET http://192.168.201.1/custom/getVideoProfiles.cgi` (respuesta separada por comas).
- [O] `http://192.168.201.1/custom/setVideoProfiles.cgi?stream0Bitrate={0}`
- [O] `http://192.168.201.1/custom/setImageSetting.cgi?` + parámetros.
- [O] `http://192.168.201.1/custom/getWifiConfig.cgi` → JSON con `AP_ESSID`, `AP_key`, `is_default_AP_key`, `channel`, `ip`, `netmask`, `gw`, etc.
- [O] Cambio de contraseña del AP: `http://192.168.201.1/custom/?cmd=301&param=network_ap&value=` + valor. [?] Formato exacto de `value`.

## 6. Assets

- [O] El APK no contiene audio, vídeos ni gráficos del juego. `data.unity3d` (2,1 MB) solo tiene la escena de carga, 6 texturas de UI genéricas y fuentes.
- [O] Nombres referenciados que viven en el OBB: `Batmobile_IOS_English.mp4`, `Batmobile_ES_ES.mp4`, `Batmobile_ES_LA.mp4`, `Batmobile_DE_DE.mp4`, `Batmobile_FR_FR.mp4`, `Batmobile_FR_CA.mp4`, `Batmobile_IT_IT.mp4`, `Batmobile_JA_JP.mp4`, `reset_batmobile_05.mp4`.
- [O] En `res/` solo hay el icono de la app (mipmaps) y dos recursos de notificación.
- Para recuperar sonidos y gráficos hace falta `main.125.com.mattel.batmobilerc.obb`.

## 7. Límites del análisis

- No se pudo ejecutar Il2CppDumper. Los metadatos IL2CPP se leyeron con un parser propio: nombres de clases, métodos, campos y literales.
- Pendiente de verificar con el vehículo real: códec de vídeo y si el firmware acepta hex en mayúsculas o con relleno.
