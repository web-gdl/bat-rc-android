"""Genera las imágenes de docs/images. Uso: python3 tools/generar_imagenes.py docs/images (requiere cairosvg)."""
import os
import sys

import cairosvg

OUT = sys.argv[1]
os.makedirs(os.path.join(OUT, "src"), exist_ok=True)

BG = "#0B0C10"
PANEL = "#15171C"
ACCENT = "#FFC400"
MUTED = "#9AA0AA"
FONT = "Inter, DejaVu Sans, sans-serif"
MONO = "DejaVu Sans Mono, monospace"


def save(name, width, height, body):
    svg = (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" '
        f'viewBox="0 0 {width} {height}" font-family="{FONT}">{body}</svg>'
    )
    with open(os.path.join(OUT, "src", name + ".svg"), "w") as f:
        f.write(svg)
    cairosvg.svg2png(bytestring=svg.encode(), write_to=os.path.join(OUT, name + ".png"), scale=2)


def button(x, y, label, w=None, active=False, size=15):
    w = w or (len(label) * size * 0.62 + 28)
    fill = ACCENT if active else "rgba(0,0,0,0.55)"
    stroke = ACCENT if active else "rgba(255,255,255,0.45)"
    color = "#000" if active else "#fff"
    return (
        f'<rect x="{x}" y="{y}" width="{w:.0f}" height="40" rx="12" fill="{fill}" stroke="{stroke}"/>'
        f'<text x="{x + w / 2:.0f}" y="{y + 26}" text-anchor="middle" font-size="{size}" '
        f'font-weight="600" fill="{color}">{label}</text>'
    ), w


def row(x, y, labels, gap=10, active=()):
    out, cx = "", x
    for label in labels:
        svg, w = button(cx, y, label, active=label in active)
        out += svg
        cx += w + gap
    return out, cx - gap - x


def pad(x, y, s, vertical, label, value=0.0):
    travel = s * 0.34
    cx, cy = x + s / 2, y + s / 2
    if vertical:
        line = f'<line x1="{cx}" y1="{cy - travel}" x2="{cx}" y2="{cy + travel}"/>'
        tx, ty = cx, cy - value * travel
    else:
        line = f'<line x1="{cx - travel}" y1="{cy}" x2="{cx + travel}" y2="{cy}"/>'
        tx, ty = cx + value * travel, cy
    return (
        f'<rect x="{x}" y="{y}" width="{s}" height="{s}" rx="24" fill="rgba(0,0,0,0.4)" '
        f'stroke="rgba(255,255,255,0.4)"/>'
        f'<g stroke="rgba(255,255,255,0.35)" stroke-width="7" stroke-linecap="round">{line}</g>'
        f'<circle cx="{tx}" cy="{ty}" r="22" fill="{ACCENT}"/>'
        f'<text x="{cx}" y="{y + 20}" text-anchor="middle" font-size="12" fill="rgba(255,255,255,0.85)">{label}</text>'
    )


def camera_scene(x, y, w, h):
    """Escena abstracta que hace de fondo de vídeo (no es una captura real)."""
    return (
        '<defs>'
        '<linearGradient id="sky" x1="0" y1="0" x2="0" y2="1">'
        '<stop offset="0" stop-color="#1C2433"/><stop offset="0.55" stop-color="#2B3445"/>'
        '<stop offset="0.56" stop-color="#3A3B3F"/><stop offset="1" stop-color="#1E1F22"/>'
        '</linearGradient></defs>'
        f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="url(#sky)"/>'
        f'<polygon points="{x + w * 0.42},{y + h * 0.56} {x + w * 0.58},{y + h * 0.56} '
        f'{x + w * 0.95},{y + h} {x + w * 0.05},{y + h}" fill="#2A2B2F"/>'
        + "".join(
            f'<rect x="{x + w * 0.5 - 3}" y="{y + h * (0.6 + i * 0.1)}" width="6" height="{h * 0.05}" '
            f'fill="{ACCENT}" opacity="0.7"/>'
            for i in range(4)
        )
        + f'<rect x="{x + w * 0.12}" y="{y + h * 0.3}" width="{w * 0.1}" height="{h * 0.26}" fill="#252D3B"/>'
        f'<rect x="{x + w * 0.76}" y="{y + h * 0.22}" width="{w * 0.12}" height="{h * 0.34}" fill="#222A37"/>'
    )


def phone(x, y, w, h, inner):
    return (
        f'<rect x="{x - 14}" y="{y - 14}" width="{w + 28}" height="{h + 28}" rx="36" fill="#05060A" '
        f'stroke="#2A2D35" stroke-width="2"/>'
        f'<svg x="{x}" y="{y}" width="{w}" height="{h}" viewBox="0 0 {w} {h}">'
        f'<clipPath id="c{x}{y}"><rect width="{w}" height="{h}" rx="24"/></clipPath>'
        f'<g clip-path="url(#c{x}{y})">{inner}</g></svg>'
    )


def controller_ui(w, h, connected=True):
    s = camera_scene(0, 0, w, h) if connected else f'<rect width="{w}" height="{h}" fill="#000"/>'
    top, _ = row(18, 16, ["Luces", "Humo", "Suspensión", "Cañón ↕"], active=("Luces",) if connected else ())
    s += top
    s += button(w - 18 - 92, 16, "Ajustes", w=92)[0]
    s += pad(18, h - 18 - 170, 170, True, "Acelerador", 0.55 if connected else 0)
    s += pad(w - 18 - 170, h - 18 - 170, 170, False, "Dirección", -0.35 if connected else 0)
    _, bw = row(0, 0, ["Motor", "Ametr.", "Cañón", "Misiles"])
    bottom, _ = row((w - bw) / 2, h - 18 - 40, ["Motor", "Ametr.", "Cañón", "Misiles"])
    s += bottom
    if connected:
        s += (
            f'<rect x="{w / 2 - 150}" y="70" width="300" height="24" rx="6" fill="rgba(0,0,0,0.55)"/>'
            f'<text x="{w / 2}" y="87" text-anchor="middle" font-family="{MONO}" font-size="12" fill="#fff">'
            "UDP → 41_ad_31_23_50_F0_00_00_00</text>"
        )
    else:
        cw, ch = 330, 200
        cx, cy = (w - cw) / 2, (h - ch) / 2 - 10
        s += (
            f'<rect x="{cx}" y="{cy}" width="{cw}" height="{ch}" rx="16" fill="{PANEL}" opacity="0.95"/>'
            f'<text x="{w / 2}" y="{cy + 40}" text-anchor="middle" font-size="18" font-weight="700" fill="#fff">'
            "Vehículo no conectado</text>"
            f'<text x="{w / 2}" y="{cy + 74}" text-anchor="middle" font-size="13.5" fill="rgba(255,255,255,0.85)">'
            "Enciende el vehículo y conecta el teléfono</text>"
            f'<text x="{w / 2}" y="{cy + 94}" text-anchor="middle" font-size="13.5" fill="rgba(255,255,255,0.85)">'
            "a su red Wi-Fi. Si Android avisa de que no</text>"
            f'<text x="{w / 2}" y="{cy + 114}" text-anchor="middle" font-size="13.5" fill="rgba(255,255,255,0.85)">'
            "tiene internet, mantén la conexión.</text>"
        )
        s += button(w / 2 - 65, cy + 138, "Abrir Wi-Fi", w=130, active=True)[0]
    return s


# 1. Banner -----------------------------------------------------------------
W, H = 1280, 420
body = f'<rect width="{W}" height="{H}" fill="{BG}"/>'
body += f'<rect x="0" y="{H - 6}" width="{W}" height="6" fill="{ACCENT}"/>'
body += (
    f'<g transform="translate(70,70)"><rect width="76" height="76" rx="20" fill="{ACCENT}"/>'
    '<g transform="scale(0.7037)" fill="#15171C">'
    '<path d="M34,62h40a6,6 0,0 1,6 6v4a6,6 0,0 1,-6 6H34a6,6 0,0 1,-6 -6v-4a6,6 0,0 1,6 -6z"/>'
    '<path d="M51,42h6v22h-6z"/><circle cx="54" cy="38" r="10"/></g></g>'
)
body += f'<text x="70" y="215" font-size="64" font-weight="800" fill="#fff">BatRC <tspan fill="{ACCENT}">Control</tspan></text>'
body += f'<text x="72" y="262" font-size="24" fill="{MUTED}">Controla el Batmobile R/C de Mattel por Wi-Fi</text>'
body += f'<text x="72" y="294" font-size="24" fill="{MUTED}">desde cualquier Android actual.</text>'
cx = 72
for chip in ["Kotlin + Compose", "Android 6 – 15", "Sin permisos", "Vídeo en vivo"]:
    w = len(chip) * 9.6 + 30
    body += (
        f'<rect x="{cx}" y="332" width="{w:.0f}" height="34" rx="17" fill="none" stroke="{ACCENT}"/>'
        f'<text x="{cx + w / 2:.0f}" y="354" text-anchor="middle" font-size="16" fill="{ACCENT}">{chip}</text>'
    )
    cx += w + 12
pw, ph = 420, 204
body += phone(800, 108, pw, ph, f'<g transform="scale({1 / 1.6})">{controller_ui(pw * 1.6, ph * 1.6)}</g>')
save("banner", W, H, body)

# 4. Estructura del paquete UDP ----------------------------------------------
W, H = 1200, 520
body = f'<rect width="{W}" height="{H}" fill="{BG}"/>'
body += f'<text x="60" y="64" font-size="28" font-weight="700" fill="#fff">Datagrama de control</text>'
body += (
    f'<text x="60" y="100" font-size="17" fill="{MUTED}">UDP → 192.168.201.1:1051 · un paquete cada 100 ms · '
    "texto ASCII, tokens hex separados por «_»</text>"
)
tokens = [
    ("41_ad", "Acelerador", "persistente", ACCENT),
    ("31_23", "Dirección", "persistente", ACCENT),
    ("A1_10", "Faros ON", "un solo envío", "#6FD3FF"),
    ("50_F0", "Cierre fijo", "siempre", "#B7BCC6"),
    ("00", "Latido", "01 cada 10", "#8BE28B"),
]
x = 60
for i, (tok, label, note, color) in enumerate(tokens):
    w = 190 if len(tok) > 2 else 120
    body += (
        f'<rect x="{x}" y="150" width="{w}" height="90" rx="14" fill="{PANEL}" stroke="{color}" stroke-width="2"/>'
        f'<text x="{x + w / 2}" y="206" text-anchor="middle" font-family="{MONO}" font-size="34" '
        f'font-weight="700" fill="{color}">{tok}</text>'
        f'<text x="{x + w / 2}" y="272" text-anchor="middle" font-size="17" font-weight="600" fill="#fff">{label}</text>'
        f'<text x="{x + w / 2}" y="296" text-anchor="middle" font-size="15" fill="{MUTED}">{note}</text>'
    )
    if i < len(tokens) - 1:
        body += (
            f'<text x="{x + w + 20}" y="208" text-anchor="middle" font-family="{MONO}" font-size="34" '
            f'fill="{MUTED}">_</text>'
        )
    x += w + 40
body += f'<line x1="60" y1="336" x2="{W - 60}" y2="336" stroke="#2A2D35"/>'
lines = [
    ("Hasta 3 comandos por paquete; si sobran, esperan al siguiente.", "#fff"),
    ("Con menos de 3 comandos se rellena con 00_00 hasta 9 tokens en total.", "#fff"),
    ("Ejes: valor 0–200 (100 = neutro). Positivo → 0x80 + v, negativo → |v| (hex en minúsculas).", "#fff"),
    ("Reposo: 41_80_31_80_50_F0_00_00_00", ACCENT),
]
for i, (text, color) in enumerate(lines):
    font = MONO if text.startswith("Reposo") else FONT
    body += f'<text x="60" y="{380 + i * 34}" font-family="{font}" font-size="18" fill="{color}">{text}</text>'
save("protocolo", W, H, body)

# 5. Arquitectura -------------------------------------------------------------
W, H = 1200, 620
body = f'<rect width="{W}" height="{H}" fill="{BG}"/>'
body += (
    '<defs><marker id="a" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto">'
    f'<path d="M0,0 L10,5 L0,10 z" fill="{MUTED}"/></marker></defs>'
)
body += f'<text x="60" y="62" font-size="28" font-weight="700" fill="#fff">Arquitectura</text>'


def box(x, y, w, h, title, sub, color="#fff", fill=PANEL):
    lines_ = "".join(
        f'<text x="{x + w / 2}" y="{y + 58 + i * 21}" text-anchor="middle" font-size="15" fill="{MUTED}">{t}</text>'
        for i, t in enumerate(sub)
    )
    return (
        f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="14" fill="{fill}" stroke="{color}" stroke-width="1.5"/>'
        f'<text x="{x + w / 2}" y="{y + 32}" text-anchor="middle" font-size="19" font-weight="700" fill="{color}">{title}</text>'
        + lines_
    )


def arrow(x1, y1, x2, y2, label=None, lx=None, ly=None):
    s = f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{MUTED}" stroke-width="2" marker-end="url(#a)"/>'
    if label:
        s += f'<text x="{lx}" y="{ly}" text-anchor="middle" font-size="14" fill="{MUTED}">{label}</text>'
    return s


body += box(60, 110, 280, 110, "Interfaz (Compose)", ["ControllerScreen · AxisPad", "SettingsDialog"])
body += box(460, 110, 280, 110, "MainViewModel", ["estado de la app", "curva y ajustes de ejes"])
body += box(60, 300, 280, 120, "CarController", ["CommandEncoder", "UDP :1051 cada 100 ms"], ACCENT)
body += box(460, 300, 280, 120, "CarVideo", ["Media3 ExoPlayer RTSP", "búfer mínimo, reintento"], ACCENT)
body += box(860, 300, 280, 120, "ToyHttp", ["CGI de la cámara", "awb_ctl · getDeviceInformation"], ACCENT)
body += box(860, 110, 280, 110, "ToyLink", ["requestNetwork (Wi-Fi sin internet)", "bindProcessToNetwork"], "#6FD3FF")
body += box(330, 500, 540, 90, "Vehículo · 192.168.201.1", ["punto de acceso Wi-Fi propio (red 192.168.201.x)"], "#8BE28B",
            "#10161A")
body += arrow(340, 165, 456, 165)
body += arrow(600, 220, 200, 296)
body += arrow(600, 220, 600, 296)
body += arrow(600, 220, 1000, 296)
body += arrow(856, 165, 744, 165, "red del vehículo", 800, 152)
body += arrow(200, 420, 480, 496, "comandos", 300, 470)
body += arrow(600, 420, 600, 496, "vídeo", 630, 465)
body += arrow(1000, 420, 720, 496, "HTTP", 890, 470)
save("arquitectura", W, H, body)
print("ok", sorted(os.listdir(OUT)))
