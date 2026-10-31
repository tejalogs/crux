#!/usr/bin/env python3
"""Generate every Crux app icon from one piece of geometry.

The mark is a bold "C" (round-capped arc) pointing at a dot, drawn on the 108-unit
Android adaptive-icon canvas. Android uses the vector XML directly
(app/src/main/res/drawable/ic_launcher_*.xml); this script produces everything else:
master SVGs, legacy Android mipmaps, the Play Store icon, desktop (png/ico/icns + Windows
appx tiles) and the Apple Icon Composer layers.

Run from the repo root:  python3 tools/brand/generate_icons.py
Needs Pillow; uses macOS `iconutil` for the .icns.
"""

import math
import shutil
import subprocess
import tempfile
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]

BRAND_VIOLET = (0x4B, 0x3B, 0xFF, 255)
WHITE = (255, 255, 255, 255)

# --- Geometry on the 108x108 adaptive canvas (keep in sync with ic_launcher_foreground.xml) ---
ARC_CENTER = (53.38, 54.0)
ARC_RADIUS = 16.38
ARC_GAP_HALF_DEG = 55.5  # opening on the right, symmetric around 0 deg
STROKE = 11.0
DOT_CENTER = (70.46, 54.0)
DOT_RADIUS = 5.85
MARK_BOUNDS = (31.5, 32.12, 76.31, 75.88)  # left, top, right, bottom incl. stroke

# The visible launcher area is the inner 72 units (18..90); icons framed this way match
# how the mark looks on an Android home screen.
LAUNCHER_VIEW = (18.0, 72.0)  # origin, size
SUPERSAMPLE = 8


def _draw_mark(draw: ImageDraw.ImageDraw, to_px, scale: float, color) -> None:
    cx, cy = to_px(*ARC_CENTER)
    r = ARC_RADIUS * scale
    w = STROKE * scale
    start, end = ARC_GAP_HALF_DEG, 360 - ARC_GAP_HALF_DEG
    # PIL draws arcs clockwise from 3 o'clock with the stroke inside the box, so pad by w/2.
    box = [cx - r - w / 2, cy - r - w / 2, cx + r + w / 2, cy + r + w / 2]
    draw.arc(box, start=start, end=end, fill=color, width=int(round(w)))
    for deg in (start, end):  # round caps
        a = math.radians(deg)
        px, py = cx + r * math.cos(a), cy + r * math.sin(a)
        draw.ellipse([px - w / 2, py - w / 2, px + w / 2, py + w / 2], fill=color)
    dx, dy = to_px(*DOT_CENTER)
    dr = DOT_RADIUS * scale
    draw.ellipse([dx - dr, dy - dr, dx + dr, dy + dr], fill=color)


def render(size, plate="rounded", view=LAUNCHER_VIEW, margin=0.0, bg=BRAND_VIOLET, fg=WHITE, height=None):
    """Render the icon.

    plate: "rounded" | "circle" | "square" | None (transparent, mark only)
    view:  (origin, size) of the 108-unit canvas region mapped onto the plate
    margin: transparent padding around the plate, as a fraction of size (macOS uses ~0.1)
    height: render a wide image (e.g. splash/wide tiles); the mark is centred
    """
    w_out, h_out = size, height or size
    S = SUPERSAMPLE
    img = Image.new("RGBA", (w_out * S, h_out * S), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    side = min(w_out, h_out) * S
    pad = side * margin
    plate_side = side - 2 * pad
    ox = (w_out * S - plate_side) / 2
    oy = (h_out * S - plate_side) / 2

    if plate is not None:
        if height:  # wide tiles: full-bleed colour, mark centred
            draw.rectangle([0, 0, w_out * S, h_out * S], fill=bg)
        elif plate == "circle":
            draw.ellipse([ox, oy, ox + plate_side, oy + plate_side], fill=bg)
        elif plate == "square":
            draw.rectangle([ox, oy, ox + plate_side, oy + plate_side], fill=bg)
        else:
            draw.rounded_rectangle([ox, oy, ox + plate_side, oy + plate_side], radius=plate_side * 0.225, fill=bg)

    origin, view_size = view
    scale = plate_side / view_size

    def to_px(x, y):
        return ox + (x - origin) * scale, oy + (y - origin) * scale

    _draw_mark(draw, to_px, scale, fg if plate is not None else bg)
    return img.resize((w_out, h_out), Image.LANCZOS)


def mark_svg(color: str) -> str:
    left, top, right, bottom = MARK_BOUNDS
    vw, vh = right - left, bottom - top
    a = math.radians(ARC_GAP_HALF_DEG)
    cx, cy = ARC_CENTER
    x = cx + ARC_RADIUS * math.cos(a)
    y1, y2 = cy + ARC_RADIUS * math.sin(a), cy - ARC_RADIUS * math.sin(a)
    width = 200
    return (
        f'<svg width="{width}" height="{width * vh / vw:.1f}" viewBox="{left} {top} {vw:.2f} {vh:.2f}" '
        'fill="none" xmlns="http://www.w3.org/2000/svg">\n'
        f'<path d="M{x:.2f},{y1:.2f} A{ARC_RADIUS},{ARC_RADIUS} 0 1 1 {x:.2f},{y2:.2f}" '
        f'stroke="{color}" stroke-width="{STROKE}" stroke-linecap="round"/>\n'
        f'<circle cx="{DOT_CENTER[0]}" cy="{DOT_CENTER[1]}" r="{DOT_RADIUS}" fill="{color}"/>\n'
        "</svg>\n"
    )


def icon_svg() -> str:
    inner = mark_svg("#FFFFFF").split("\n", 1)[1].rsplit("</svg>", 1)[0]
    return (
        '<svg width="1024" height="1024" viewBox="18 18 72 72" xmlns="http://www.w3.org/2000/svg">\n'
        '<rect x="18" y="18" width="72" height="72" rx="16.2" fill="#4B3BFF"/>\n'
        f"{inner}</svg>\n"
    )


def save(img, path: Path, **kw):
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, **kw)


def main():
    # Masters
    brand = ROOT / "docs/brand"
    brand.mkdir(parents=True, exist_ok=True)
    (brand / "crux-mark.svg").write_text(mark_svg("#4B3BFF"))
    (brand / "crux-mark-white.svg").write_text(mark_svg("#FFFFFF"))
    (brand / "crux-icon.svg").write_text(icon_svg())
    save(render(1024), brand / "crux-icon-1024.png")
    save(render(512, plate="square"), brand / "play-store-icon-512.png")  # Play applies its own mask

    # Android legacy mipmaps (pre-API-26 launchers; adaptive XML covers the rest)
    for density, px in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
        res = ROOT / f"app/src/main/res/mipmap-{density}"
        save(render(px), res / "ic_launcher.webp", lossless=True)
        save(render(px, plate="circle"), res / "ic_launcher_round.webp", lossless=True)

    # Desktop
    desk = ROOT / "desktopApp/resources"
    save(render(512, margin=0.04), desk / "ic_launcher.png")
    ico_sizes = [16, 24, 32, 48, 64, 128, 256]
    render(256, margin=0.04).save(desk / "ic_launcher.ico", sizes=[(s, s) for s in ico_sizes])
    with tempfile.TemporaryDirectory() as tmp:
        iconset = Path(tmp) / "ic_launcher.iconset"
        iconset.mkdir()
        for base in (16, 32, 128, 256, 512):
            for mult, suffix in ((1, ""), (2, "@2x")):
                px = base * mult
                render(px, margin=0.1).save(iconset / f"icon_{base}x{base}{suffix}.png")
        subprocess.run(["iconutil", "-c", "icns", str(iconset), "-o", str(desk / "ic_launcher.icns")], check=True)

    # Windows appx tiles: keep existing filenames and sizes
    for png in sorted((desk / "appx").glob("*.png")):
        with Image.open(png) as old:
            w, h = old.size
        name = png.name
        if "unplated" in name:
            img = render(w, plate=None, view=(29.0, 50.0))  # glyph only, brand colour, transparent
        elif w != h:
            img = render(w, plate="square", height=h, view=(0.0, 108.0))
        else:
            img = render(w, plate="square")
        img.save(png)

    # Apple Icon Composer layers (same art in every colour variant; the variant sets the fill)
    for icon_dir in sorted((ROOT / "appleApp").glob("*/AppIcon*.icon")):
        for layer in ("Group.svg", "Group-2.svg"):
            (icon_dir / "Assets" / layer).write_text(mark_svg("#FFFFFF"))

    print("Crux icons generated.")


if __name__ == "__main__":
    main()
