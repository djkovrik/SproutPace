#!/usr/bin/env python3
"""Render deterministic Android and iOS derivatives of ASSET-001.

The geometry and colors below mirror logo_sproutpace.xml. Native icons use an
opaque warm-neutral background; Android adaptive foreground/monochrome layers
retain transparent padding for platform masks.
"""

from __future__ import annotations

from pathlib import Path
from typing import Iterable

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[3]
ANDROID_RES = ROOT / "androidApp/src/main/res"
IOS_ICONSET = ROOT / "iosApp/iosApp/Assets.xcassets/AppIcon.appiconset"
CONTACT_SHEET = ROOT / "docs/assets/platform-icon-contact-sheet.png"

BACKGROUND = "#F9FAF3"
STEM = "#2F6B45"
LEFT_LEAF = "#4F8A5B"
RIGHT_LEAF = "#76A96B"
TRAY = "#795548"
TRAY_HIGHLIGHT = "#D5B27A"


def cubic(
    start: tuple[float, float],
    control1: tuple[float, float],
    control2: tuple[float, float],
    end: tuple[float, float],
    steps: int = 32,
) -> list[tuple[float, float]]:
    points = []
    for index in range(steps + 1):
        t = index / steps
        inverse = 1.0 - t
        x = (
            inverse**3 * start[0]
            + 3 * inverse**2 * t * control1[0]
            + 3 * inverse * t**2 * control2[0]
            + t**3 * end[0]
        )
        y = (
            inverse**3 * start[1]
            + 3 * inverse**2 * t * control1[1]
            + 3 * inverse * t**2 * control2[1]
            + t**3 * end[1]
        )
        points.append((x, y))
    return points


def transform(
    points: Iterable[tuple[float, float]],
    origin: float,
    size: float,
) -> list[tuple[float, float]]:
    return [(origin + x * size / 64.0, origin + y * size / 64.0) for x, y in points]


def draw_mark(image: Image.Image, mark_size: float, monochrome: bool = False) -> None:
    draw = ImageDraw.Draw(image)
    origin = (image.width - mark_size) / 2.0
    color = "#000000" if monochrome else None

    def polygon(points: Iterable[tuple[float, float]], fill: str) -> None:
        draw.polygon(transform(points, origin, mark_size), fill=color or fill)

    polygon([(30, 45), (34, 45), (34, 26), (30, 26)], STEM)
    polygon(
        cubic((32, 29), (22, 29), (16, 23), (16, 14))
        + cubic((16, 14), (26, 14), (32, 20), (32, 29)),
        LEFT_LEAF,
    )
    polygon(
        cubic((32, 24), (33, 15), (39, 10), (49, 10))
        + cubic((49, 10), (49, 19), (42, 25), (32, 24)),
        RIGHT_LEAF,
    )
    polygon([(10, 42), (54, 42), (50, 55), (14, 55)], TRAY)
    polygon([(14, 45), (50, 45), (48, 51), (16, 51)], TRAY_HIGHLIGHT)


def render(size: int, *, background: str | None, mark_fraction: float, monochrome: bool = False) -> Image.Image:
    scale = 4
    image = Image.new("RGBA", (size * scale, size * scale), background or (0, 0, 0, 0))
    draw_mark(image, image.width * mark_fraction, monochrome)
    return image.resize((size, size), Image.Resampling.LANCZOS)


def save_android() -> None:
    densities = {
        "mdpi": (48, 108),
        "hdpi": (72, 162),
        "xhdpi": (96, 216),
        "xxhdpi": (144, 324),
        "xxxhdpi": (192, 432),
    }
    for density, (legacy_size, adaptive_size) in densities.items():
        destination = ANDROID_RES / f"mipmap-{density}"
        legacy = render(legacy_size, background=None, mark_fraction=0.72)
        legacy_background = Image.new("RGBA", legacy.size, (0, 0, 0, 0))
        inset = max(1, round(legacy_size * 0.04))
        ImageDraw.Draw(legacy_background).rounded_rectangle(
            (inset, inset, legacy_size - inset - 1, legacy_size - inset - 1),
            radius=legacy_size * 0.22,
            fill=BACKGROUND,
        )
        legacy_background.alpha_composite(legacy)
        legacy_background.save(destination / "ic_launcher.png", optimize=True)
        render(adaptive_size, background=BACKGROUND, mark_fraction=0.60).save(
            destination / "ic_launcher_background.png",
            optimize=True,
        )
        render(adaptive_size, background=None, mark_fraction=0.60).save(
            destination / "ic_launcher_foreground.png",
            optimize=True,
        )
        render(adaptive_size, background=None, mark_fraction=0.60, monochrome=True).save(
            destination / "ic_launcher_monochrome.png",
            optimize=True,
        )


def save_ios() -> None:
    sizes = {
        "AppIcon-20@2x.png": 40,
        "AppIcon-20@2x~ipad.png": 40,
        "AppIcon-20@3x.png": 60,
        "AppIcon-20~ipad.png": 20,
        "AppIcon-29.png": 29,
        "AppIcon-29@2x.png": 58,
        "AppIcon-29@2x~ipad.png": 58,
        "AppIcon-29@3x.png": 87,
        "AppIcon-29~ipad.png": 29,
        "AppIcon-40@2x.png": 80,
        "AppIcon-40@2x~ipad.png": 80,
        "AppIcon-40@3x.png": 120,
        "AppIcon-40~ipad.png": 40,
        "AppIcon-60@2x~car.png": 120,
        "AppIcon-60@3x~car.png": 180,
        "AppIcon-83.5@2x~ipad.png": 167,
        "AppIcon@2x.png": 120,
        "AppIcon@2x~ipad.png": 152,
        "AppIcon@3x.png": 180,
        "AppIcon~ios-marketing.png": 1024,
        "AppIcon~ipad.png": 76,
    }
    for filename, size in sizes.items():
        # App Store icons must be opaque; the OS supplies the final device mask.
        render(size, background=BACKGROUND, mark_fraction=0.68).convert("RGB").save(
            IOS_ICONSET / filename,
            optimize=True,
        )


def save_contact_sheet() -> None:
    cells: list[tuple[str, Image.Image]] = []
    for density in ("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"):
        source = ANDROID_RES / f"mipmap-{density}"
        legacy = Image.open(source / "ic_launcher.png").convert("RGBA")
        background = Image.open(source / "ic_launcher_background.png").convert("RGBA")
        foreground = Image.open(source / "ic_launcher_foreground.png").convert("RGBA")
        monochrome = Image.open(source / "ic_launcher_monochrome.png").convert("RGBA")
        cells.extend(
            (
                (f"Android legacy {density}", legacy),
                (f"Android adaptive {density}", Image.alpha_composite(background, foreground)),
                (f"Android monochrome {density}", monochrome),
            ),
        )
    for filename in (
        "AppIcon-20~ipad.png",
        "AppIcon@3x.png",
        "AppIcon-83.5@2x~ipad.png",
        "AppIcon~ios-marketing.png",
    ):
        cells.append((f"iOS {filename}", Image.open(IOS_ICONSET / filename).convert("RGBA")))

    sheet = Image.new("RGB", (5 * 240, 4 * 260), "white")
    draw = ImageDraw.Draw(sheet)
    for index, (label, source) in enumerate(cells):
        row, column = divmod(index, 5)
        source.thumbnail((200, 210))
        x = column * 240 + (220 - source.width) // 2
        y = row * 260 + 28 + (210 - source.height) // 2
        checker = Image.new("RGB", source.size, (232, 232, 232))
        checker_draw = ImageDraw.Draw(checker)
        checker_draw.rectangle((0, 0, source.width // 2, source.height // 2), fill=(200, 200, 200))
        checker_draw.rectangle(
            (source.width // 2, source.height // 2, source.width, source.height),
            fill=(200, 200, 200),
        )
        sheet.paste(checker, (x, y))
        sheet.paste(source.convert("RGB"), (x, y), source.getchannel("A"))
        draw.text((column * 240 + 4, row * 260 + 5), label, fill="black")
    sheet.save(CONTACT_SHEET, optimize=True)


if __name__ == "__main__":
    save_android()
    save_ios()
    save_contact_sheet()
