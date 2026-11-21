#!/usr/bin/env bash
# Instala la app de depuración en un emulador y guarda capturas de pantalla
# reales: la pantalla de inicio y una escena congelada de cada efecto.
set -euo pipefail

OUT=${1:-capturas}
APP=org.batrc.controller
mkdir -p "$OUT"

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1

shot() {
  local name=$1
  shift
  adb shell am force-stop "$APP"
  adb shell am start -W -n "$APP/.MainActivity" "$@"
  sleep 5
  adb exec-out screencap -p > "$OUT/$name.png"
  echo "captura: $name"
}

shot inicio
shot ametralladora --es fx_demo gun --ef fx_time 0.37
shot canon --es fx_demo cannon --ef fx_time 0.18
shot misiles --es fx_demo missiles --ef fx_time 1.25
shot impacto --es fx_demo missiles --ef fx_time 2.12
shot objetivos --es fx_demo targets --ef fx_time 3.62

adb logcat -d -b crash > "$OUT/errores.txt" || true
