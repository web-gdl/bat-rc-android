#!/usr/bin/env python3
"""Prueba rápida del protocolo desde un ordenador conectado al Wi-Fi del vehículo.

Enciende los faros 2 s, los apaga y termina. No mueve el motor.
Uso: python3 udp_test.py
"""
import socket
import time

HOST, PORT = "192.168.201.1", 1051
IDLE = "41_80_31_80_50_F0_00_00_"

sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)


def send(first=None, seconds=2.0):
    """Envía `first` una vez y después paquetes de reposo cada 100 ms."""
    pending = first
    for tick in range(int(seconds * 10)):
        beat = "01" if tick % 10 == 9 else "00"
        if pending:
            packet = f"{pending}_50_F0_00_00_00_00_{beat}"
            pending = None
        else:
            packet = IDLE + beat
        sock.sendto(packet.encode("ascii"), (HOST, PORT))
        print(packet)
        time.sleep(0.1)


send(seconds=1.0)          # reposo
send("A1_10")              # faros encendidos
send("A1_01", seconds=1.0)  # faros apagados
