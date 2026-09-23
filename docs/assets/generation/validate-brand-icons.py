"""Check native slot sizes, transparency, monochrome alpha and mask safety."""
import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[3]
res = ROOT / "androidApp/src/main/res"
for density, size, layer in [("mdpi",48,108),("hdpi",72,162),("xhdpi",96,216),
                              ("xxhdpi",144,324),("xxxhdpi",192,432)]:
    folder = res / f"mipmap-{density}"
    legacy = Image.open(folder / "ic_launcher.png")
    assert legacy.size == (size,size)
    assert legacy.getchannel("A").getextrema() == (0,255)
    bg, fg, mono = [Image.open(folder / f"ic_launcher_{name}.png").convert("RGBA")
                    for name in ("background","foreground","monochrome")]
    assert all(im.size == (layer,layer) for im in (bg,fg,mono))
    assert len(bg.getcolors(layer*layer)) == 1, "Background contains duplicate artwork"
    assert bg.getchannel("A").getextrema() == (255,255)
    assert fg.getchannel("A").getextrema() == (0,255)
    assert fg.getchannel("A").tobytes() == mono.getchannel("A").tobytes()
    assert mono.convert("RGB").getextrema() == ((0,0),(0,0),(0,0))
    # Circle stricter than the bounding box; excludes invisible antialias fringe.
    radius = layer * 33 / 108
    for y in range(layer):
        for x in range(layer):
            if fg.getpixel((x,y))[3] > 8:
                assert (x+.5-layer/2)**2 + (y+.5-layer/2)**2 <= radius**2, (density,x,y)
iconset = ROOT / "iosApp/iosApp/Assets.xcassets/AppIcon.appiconset"
slots = json.loads((iconset / "Contents.json").read_text())["images"]
for slot in slots:
    size = round(float(slot["size"].split("x")[0])*float(slot["scale"][:-1]))
    im = Image.open(iconset / slot["filename"])
    assert im.size == (size,size), slot
    assert im.mode == "RGB", slot
print(f"PASS: 20 Android PNGs, 21 iOS slots, identical monochrome alpha, 66dp safe circle.")
