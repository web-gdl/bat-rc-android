"""Sintetiza los efectos de sonido de la app (originales, sin muestras de terceros).

Uso: python3 tools/generar_sonidos.py app/src/main/res/raw
Requiere numpy. Genera WAV mono de 16 bits a 22 050 Hz.
"""
import os
import sys
import wave

import numpy as np

RATE = 22050
OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
rng = np.random.default_rng(1051)


def t(seconds):
    return np.arange(int(RATE * seconds)) / RATE


def lowpass(x, cutoff):
    """Filtro de un polo; cutoff puede ser escalar o un array por muestra."""
    cutoff = np.broadcast_to(np.asarray(cutoff, dtype=float), x.shape)
    alpha = 1 - np.exp(-2 * np.pi * cutoff / RATE)
    y = np.empty_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc += alpha[i] * (v - acc)
        y[i] = acc
    return y


def env(n, attack, release_curve):
    a = max(1, int(RATE * attack))
    e = np.ones(n)
    e[:a] = np.linspace(0, 1, a)
    e[a:] = release_curve(np.linspace(0, 1, n - a))
    return e


def save(name, x, peak=0.89):
    x = x / (np.max(np.abs(x)) + 1e-9) * peak
    fade = min(len(x), int(RATE * 0.01))
    x[-fade:] *= np.linspace(1, 0, fade)
    data = (x * 32767).astype("<i2")
    with wave.open(os.path.join(OUT, name + ".wav"), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(data.tobytes())
    print(name, f"{len(x) / RATE:.2f}s")


def noise(n):
    return rng.standard_normal(n)


# Motor: acelerón de un V8 (dientes de sierra con subida y bajada de revoluciones).
def engine():
    tt = t(1.6)
    rpm = 38 + 70 * np.sin(np.clip(tt / 1.6, 0, 1) * np.pi) ** 1.5
    phase = np.cumsum(rpm) / RATE
    saw = 2 * (phase % 1) - 1
    saw2 = 2 * ((phase * 2.01) % 1) - 1
    rumble = 0.7 * saw + 0.35 * saw2 + 0.25 * noise(len(tt))
    x = lowpass(rumble, 300 + 900 * (rpm - 38) / 70)
    x *= env(len(tt), 0.04, lambda u: (1 - u) ** 0.6)
    return x


# Ametralladora: bucle de disparos a ~14 por segundo.
def machine_gun():
    tt = t(1.0)
    x = np.zeros(len(tt))
    period = int(RATE / 14)
    for start in range(0, len(tt), period):
        n = min(period, len(tt) - start)
        shot = noise(n) * np.exp(-np.linspace(0, 9, n))
        thump = np.sin(2 * np.pi * 95 * np.arange(n) / RATE) * np.exp(-np.linspace(0, 7, n))
        x[start:start + n] += 0.8 * lowpass(shot, 2500) + 0.6 * thump
    return x


# Cañón: golpe grave con cola larga.
def cannon():
    tt = t(1.8)
    n = len(tt)
    freq = 70 * np.exp(-tt * 2.2) + 32
    boom = np.sin(2 * np.pi * np.cumsum(freq) / RATE) * np.exp(-tt * 2.6)
    crack = lowpass(noise(n), 1800) * np.exp(-tt * 18)
    tail = lowpass(noise(n), 260) * np.exp(-tt * 1.9)
    return 1.0 * boom + 0.9 * crack + 0.9 * tail


# Misil: silbido de lanzamiento que se aleja.
def missile():
    tt = t(2.0)
    n = len(tt)
    cutoff = 400 + 3800 * np.exp(-tt * 1.4)
    hiss = lowpass(noise(n), cutoff)
    whistle = 0.25 * np.sin(2 * np.pi * np.cumsum(900 - 300 * tt) / RATE)
    ignition = lowpass(noise(n), 700) * np.exp(-tt * 14) * 2.0
    x = (hiss + whistle) * env(n, 0.03, lambda u: (1 - u) ** 1.4) + ignition
    return x


# Explosión: impacto del misil.
def explosion():
    tt = t(1.6)
    n = len(tt)
    body = lowpass(noise(n), 900 * np.exp(-tt * 2.5) + 120) * np.exp(-tt * 2.4)
    low = np.sin(2 * np.pi * np.cumsum(55 * np.exp(-tt * 1.5) + 25) / RATE) * np.exp(-tt * 3)
    debris = lowpass(noise(n), 3000) * np.exp(-tt * 9) * 0.5
    return body * 1.4 + low + debris


save("fx_engine", engine())
save("fx_machine_gun", machine_gun())
save("fx_cannon", cannon())
save("fx_missile", missile())
save("fx_explosion", explosion())
