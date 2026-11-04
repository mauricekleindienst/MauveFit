#!/usr/bin/env python3
# Usage: scripts/play-art.py <images dir> app/src/main/res/font/archivo.ttf "<tagline>"
# Renders the Play Store icon (512x512) and feature graphic (1024x500) from the launcher mark.
import sys, math
from PIL import Image, ImageDraw, ImageFilter, ImageFont
out = sys.argv[1]
PTS = [(28.4,62.8),(39.6,62.8),(42.8,67.6),(47.6,40.4),(54,58),(60.4,40.4),(65.2,67.6),(68.4,62.8),(79.6,62.8)]
COPPER = (0xDB, 0x90, 0x62)

def background(w, h, cx, cy, r):
    img = Image.new("RGB", (w, h))
    px = img.load()
    a, b = (0x2A, 0x2A, 0x33), (0x0F, 0x0F, 0x13)
    for y in range(h):
        for x in range(w):
            t = min(1.0, math.hypot(x - cx, y - cy) / r)
            px[x, y] = tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))
    return img

def trace(img, scale, ox, oy, width):
    S = 4  # supersampling
    W, H = img.size
    layer = Image.new("RGBA", (W * S, H * S), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    pts = [((x * scale + ox) * S, (y * scale + oy) * S) for x, y in PTS]
    x0, x1 = pts[0][0], pts[-1][0]
    # Fade like the vector: 15 % alpha at the ends, full between 22 % and 78 %.
    def alpha(x):
        t = (x - x0) / (x1 - x0)
        if t < 0.22: return 0.15 + 0.85 * t / 0.22
        if t > 0.78: return 0.15 + 0.85 * (1 - t) / 0.22
        return 1.0
    steps = 400
    for i in range(len(pts) - 1):
        (ax, ay), (bx, by) = pts[i], pts[i + 1]
        for k in range(steps):
            t = k / steps
            x, y = ax + (bx - ax) * t, ay + (by - ay) * t
            r = width * S / 2
            d.ellipse((x - r, y - r, x + r, y + r), fill=COPPER + (int(255 * alpha(x)),))
    layer = layer.resize((W, H), Image.LANCZOS)
    img.paste(layer, (0, 0), layer)

icon = background(512, 512, 0.25 * 512, 0.15 * 512, 1.1 * 512)
trace(icon, 512 / 108 * 1.25, -54 * 512 / 108 * 1.25 + 256, -54 * 512 / 108 * 1.25 + 256 + 8, 4 * 512 / 108 * 1.25)
icon.save(f"{out}/icon.png")

fg = background(1024, 500, 0.2 * 1024, 0.1 * 500, 1.1 * 1024)
trace(fg, 3.4, 70 - 28.4 * 3.4, 250 - 54 * 3.4, 4 * 3.4)
d = ImageDraw.Draw(fg)
left, right = 330, 1024 - 56

def fit(text, size, weight, width):
    # The largest size up to [size] at which [text] fits between left and right.
    while True:
        f = ImageFont.truetype(sys.argv[2], size)
        f.set_variation_by_axes([weight, width])
        if d.textlength(text, font=f) <= right - left or size <= 12:
            return f
        size -= 2

title = fit("Mauve Fit", 96, 800, 125)
sub = fit(sys.argv[3], 34, 500, 100)
d.text((left, 250), "Mauve Fit", font=title, fill=(0xF2, 0xF3, 0xF5), anchor="ls")
d.text((left + 2, 304), sys.argv[3], font=sub, fill=(0xA6, 0xAC, 0xB4), anchor="ls")
fg.save(f"{out}/featureGraphic.png")
