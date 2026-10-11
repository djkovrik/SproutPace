"""Build labelled QA sheets from existing Paparazzi PNGs; never alters goldens.

Usage: python preview-contact-sheets.py INPUT_DIRECTORY OUTPUT_DIRECTORY
Requires Pillow. Individual full-resolution PNGs remain the review authority.
"""
import argparse
import json
import re
from pathlib import Path
from PIL import Image, ImageDraw


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    source, destination = args.input.resolve(), args.output.resolve()
    if source == destination or source in destination.parents:
        parser.error("Output must be outside the golden input directory")
    images = sorted(source.glob("*.png"))
    destination.mkdir(parents=True, exist_ok=True)
    manifest = []
    for page in range(0, len(images), 12):
        sheet = Image.new("RGB", (1200, 1800), "#eeeeee")
        draw = ImageDraw.Draw(sheet)
        for slot, path in enumerate(images[page:page + 12]):
            x, y = slot % 4 * 300, slot // 4 * 600
            with Image.open(path) as original:
                preview = original.convert("RGB")
                preview.thumbnail((288, 510))
                sheet.paste(preview, (x + (300 - preview.width) // 2, y))
                label = re.sub(r"^.*?_snapshot\[\d+\]_", "", path.stem)
                for row in range(0, min(len(label), 180), 38):
                    draw.text((x + 5, y + 515 + row // 38 * 14), label[row:row + 38], fill="black")
                manifest.append({"file": str(path), "sheet": page // 12 + 1, "slot": slot + 1,
                                 "width": original.width, "height": original.height})
        sheet.save(destination / f"sheet-{page // 12 + 1:03}.png")
    (destination / "manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print(f"{len(images)} screenshots, {(len(images) + 11) // 12} sheets in {destination}")


if __name__ == "__main__":
    main()
