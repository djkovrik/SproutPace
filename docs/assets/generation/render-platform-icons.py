#!/usr/bin/env python3
"""Deterministic size/mask exports of the approved 1B-1 ImageGen cutout.

Foreground and monochrome share the same alpha. The adaptive background is a
solid color, not a second copy of the logo. Requires Pillow.
"""
from __future__ import annotations

import json
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[3]
ANDROID_RES = ROOT / "androidApp/src/main/res"
IOS_ICONSET = ROOT / "iosApp/iosApp/Assets.xcassets/AppIcon.appiconset"
SOURCE = ROOT / "docs/assets/source/logo-1b-1-transparent.png"
BACKGROUND = "#FAF8F0"
DENSITIES = {"mdpi": (48, 108), "hdpi": (72, 162), "xhdpi": (96, 216),
             "xxhdpi": (144, 324), "xxxhdpi": (192, 432)}


def render(size: int, fraction: float, background: str | None = None,
           monochrome: bool = False) -> Image.Image:
    source = Image.open(SOURCE).convert("RGBA")
    # Ignore almost-invisible generator padding when measuring optical bounds.
    bounds = source.getchannel("A").point(lambda value: 255 if value > 8 else 0).getbbox()
    source = source.crop(bounds)
    source.thumbnail((round(size * fraction), round(size * fraction)), Image.Resampling.LANCZOS)
    if monochrome:
        alpha = source.getchannel("A")
        source = Image.new("RGBA", source.size, "black")
        source.putalpha(alpha)
    output = Image.new("RGBA", (size, size), background or (0, 0, 0, 0))
    output.alpha_composite(source, ((size - source.width) // 2, (size - source.height) // 2))
    return output


def save_android() -> None:
    for density, (legacy_size, adaptive_size) in DENSITIES.items():
        destination = ANDROID_RES / f"mipmap-{density}"
        canvas = Image.new("RGBA", (legacy_size * 4, legacy_size * 4))
        inset = round(canvas.width * .04)
        ImageDraw.Draw(canvas).rounded_rectangle(
            (inset, inset, canvas.width - inset - 1, canvas.height - inset - 1),
            radius=canvas.width * .22, fill=BACKGROUND)
        canvas.alpha_composite(render(canvas.width, .68))
        canvas.resize((legacy_size, legacy_size), Image.Resampling.LANCZOS).save(
            destination / "ic_launcher.png", optimize=True)
        Image.new("RGBA", (adaptive_size, adaptive_size), BACKGROUND).save(
            destination / "ic_launcher_background.png", optimize=True)
        # Actual silhouette fits the 66dp safe circle on a 108dp adaptive layer.
        render(adaptive_size, .48).save(destination / "ic_launcher_foreground.png", optimize=True)
        render(adaptive_size, .48, monochrome=True).save(
            destination / "ic_launcher_monochrome.png", optimize=True)


def save_ios() -> None:
    slots = json.loads((IOS_ICONSET / "Contents.json").read_text())["images"]
    for slot in slots:
        size = round(float(slot["size"].split("x")[0]) * float(slot["scale"].rstrip("x")))
        render(size, .68, BACKGROUND).convert("RGB").save(IOS_ICONSET / slot["filename"], optimize=True)


def save_contact_sheet() -> None:
    cells = []
    for density in DENSITIES:
        folder = ANDROID_RES / f"mipmap-{density}"
        foreground = Image.open(folder / "ic_launcher_foreground.png").convert("RGBA")
        background = Image.open(folder / "ic_launcher_background.png").convert("RGBA")
        combined = Image.alpha_composite(background, foreground)
        inset = combined.width // 6
        combined = combined.crop((inset, inset, combined.width - inset, combined.height - inset))
        circle = Image.new("L", combined.size)
        ImageDraw.Draw(circle).ellipse((0, 0, circle.width-1, circle.height-1), fill=255)
        combined.putalpha(circle)
        cells.extend([(f"Android {density}", Image.open(folder / "ic_launcher.png")),
                      (f"Adaptive circle {density}", combined),
                      (f"Monochrome {density}", Image.open(folder / "ic_launcher_monochrome.png"))])
    for name in ("AppIcon-20~ipad.png", "AppIcon@3x.png", "AppIcon-83.5@2x~ipad.png",
                 "AppIcon~ios-marketing.png"):
        cells.append((f"iOS {name}", Image.open(IOS_ICONSET / name)))
    sheet = Image.new("RGB", (1200, 1040), "#E7E9E2")
    draw = ImageDraw.Draw(sheet)
    for index, (label, original) in enumerate(cells):
        source = original.convert("RGBA")
        source.thumbnail((200, 210))
        row, column = divmod(index, 5)
        x, y = column * 240 + (240-source.width)//2, row * 260 + 30 + (210-source.height)//2
        sheet.paste(source, (x, y), source.getchannel("A"))
        draw.text((column * 240 + 6, row * 260 + 8), label, fill="black")
    sheet.save(ROOT / "docs/assets/platform-icon-contact-sheet.png")


if __name__ == "__main__":
    save_android()
    save_ios()
    render(512, .90).save(ROOT / "shared/compose/src/commonMain/composeResources/drawable/logo_sproutpace.png")
    save_contact_sheet()
