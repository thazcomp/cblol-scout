#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Gerador dos elementos gráficos da Play Store para o Rift Manager.

Gera:
  - icon_512.png          → ícone do app 512x512 (obrigatório na loja)
  - feature_graphic.png   → banner 1024x500 (obrigatório na loja)

Requer Pillow:  pip install pillow

Uso:
  python generate_store_assets.py
"""

from PIL import Image, ImageDraw, ImageFont
import math
import os

# ── Paleta (mesma do app) ──────────────────────────────────────────────────
BG_DARK     = (10, 11, 26)      # #0A0B1A
SURFACE     = (22, 24, 48)      # #161830
GOLD        = (240, 182, 56)    # #F0B638
GOLD_LIGHT  = (255, 216, 118)   # #FFD876
TEXT        = (245, 237, 216)   # #F5EDD8
MUTED       = (168, 162, 189)   # #A8A2BD

OUT_DIR = os.path.dirname(os.path.abspath(__file__))


def draw_trophy(draw, cx, cy, scale, gold=GOLD, gold_light=GOLD_LIGHT, dark=BG_DARK):
    """Desenha um troféu dourado centrado em (cx, cy). `scale` controla o tamanho."""
    s = scale

    # Taça (corpo principal) — trapézio arredondado embaixo
    cup_top_w = 1.6 * s
    cup_top_y = cy - 1.3 * s
    cup_bot_y = cy + 0.5 * s
    left  = cx - cup_top_w / 2
    right = cx + cup_top_w / 2

    # Corpo da taça com base curva
    draw.polygon(
        [(left, cup_top_y), (right, cup_top_y),
         (cx + 0.95 * s, cy - 0.1 * s), (cx + 0.55 * s, cup_bot_y),
         (cx - 0.55 * s, cup_bot_y), (cx - 0.95 * s, cy - 0.1 * s)],
        fill=gold
    )
    # Arredonda a base da taça
    draw.ellipse(
        [cx - 0.55 * s, cup_bot_y - 0.35 * s, cx + 0.55 * s, cup_bot_y + 0.35 * s],
        fill=gold
    )

    # Alças (arcos laterais)
    handle_w = int(max(2, 0.18 * s))
    # Esquerda
    draw.arc(
        [cx - 1.5 * s, cup_top_y, cx - 0.4 * s, cy + 0.1 * s],
        start=40, end=320, fill=gold, width=handle_w
    )
    # Direita
    draw.arc(
        [cx + 0.4 * s, cup_top_y, cx + 1.5 * s, cy + 0.1 * s],
        start=-140, end=140, fill=gold, width=handle_w
    )

    # Haste
    draw.rectangle(
        [cx - 0.18 * s, cup_bot_y, cx + 0.18 * s, cy + 1.1 * s],
        fill=gold
    )

    # Base (pedestal) — dois níveis
    draw.polygon(
        [(cx - 0.5 * s, cy + 1.1 * s), (cx + 0.5 * s, cy + 1.1 * s),
         (cx + 0.7 * s, cy + 1.5 * s), (cx - 0.7 * s, cy + 1.5 * s)],
        fill=gold_light
    )
    draw.rectangle(
        [cx - 0.85 * s, cy + 1.5 * s, cx + 0.85 * s, cy + 1.75 * s],
        fill=gold_light
    )

    # Estrela central na taça
    draw_star(draw, cx, cy - 0.45 * s, 0.42 * s, 0.18 * s, fill=dark)


def draw_star(draw, cx, cy, outer_r, inner_r, fill, points=5):
    """Desenha uma estrela de `points` pontas."""
    verts = []
    for i in range(points * 2):
        angle = math.pi / points * i - math.pi / 2
        r = outer_r if i % 2 == 0 else inner_r
        verts.append((cx + r * math.cos(angle), cy + r * math.sin(angle)))
    draw.polygon(verts, fill=fill)


def make_icon(size=512):
    """Ícone do app 512x512 com fundo arredondado escuro + troféu dourado."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # Fundo arredondado (squircle simples via rounded rectangle)
    radius = int(size * 0.22)
    draw.rounded_rectangle([0, 0, size - 1, size - 1], radius=radius, fill=BG_DARK)

    # Anel dourado sutil de borda
    draw.rounded_rectangle(
        [int(size * 0.045), int(size * 0.045), int(size * 0.955), int(size * 0.955)],
        radius=int(radius * 0.82), outline=GOLD, width=max(2, int(size * 0.008))
    )

    # Troféu centralizado
    draw_trophy(draw, size / 2, size / 2 - size * 0.02, scale=size * 0.16)

    img.save(os.path.join(OUT_DIR, "icon_512.png"))
    print("✓ icon_512.png gerado")


def _load_font(size, bold=True):
    """Tenta carregar uma fonte do sistema; cai para a default se não achar."""
    candidates = [
        "C:/Windows/Fonts/segoeuib.ttf" if bold else "C:/Windows/Fonts/segoeui.ttf",
        "C:/Windows/Fonts/arialbd.ttf" if bold else "C:/Windows/Fonts/arial.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold
            else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ]
    for path in candidates:
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def make_feature_graphic(w=1024, h=500):
    """Banner 1024x500 com gradiente, troféu à esquerda e título à direita."""
    img = Image.new("RGB", (w, h), BG_DARK)
    draw = ImageDraw.Draw(img)

    # Gradiente vertical sutil (escuro -> levemente roxo)
    for y in range(h):
        t = y / h
        r = int(BG_DARK[0] + (SURFACE[0] - BG_DARK[0]) * t)
        g = int(BG_DARK[1] + (SURFACE[1] - BG_DARK[1]) * t)
        b = int(BG_DARK[2] + (SURFACE[2] - BG_DARK[2]) * t)
        draw.line([(0, y), (w, y)], fill=(r, g, b))

    # Brilho radial dourado atrás do troféu (à esquerda)
    glow_cx, glow_cy = int(w * 0.24), int(h * 0.5)
    glow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    gdraw = ImageDraw.Draw(glow)
    for rr in range(int(h * 0.55), 0, -4):
        alpha = int(38 * (1 - rr / (h * 0.55)))
        gdraw.ellipse([glow_cx - rr, glow_cy - rr, glow_cx + rr, glow_cy + rr],
                      fill=(GOLD[0], GOLD[1], GOLD[2], alpha))
    img = Image.alpha_composite(img.convert("RGBA"), glow).convert("RGB")
    draw = ImageDraw.Draw(img)

    # Troféu à esquerda
    draw_trophy(draw, w * 0.24, h * 0.5, scale=h * 0.17)

    # Título e subtítulo à direita
    font_title = _load_font(int(h * 0.18), bold=True)
    font_sub   = _load_font(int(h * 0.072), bold=False)

    title = "RIFT MANAGER"
    sub   = "Seja o técnico campeão do cenário de e-sports"

    tx = int(w * 0.42)
    # Título
    draw.text((tx, int(h * 0.30)), title, font=font_title, fill=GOLD)
    # Subtítulo
    draw.text((tx, int(h * 0.56)), sub, font=font_sub, fill=TEXT)

    img.save(os.path.join(OUT_DIR, "feature_graphic.png"))
    print("✓ feature_graphic.png gerado")


if __name__ == "__main__":
    make_icon()
    make_feature_graphic()
    print("\nPronto! Arquivos em:", OUT_DIR)
