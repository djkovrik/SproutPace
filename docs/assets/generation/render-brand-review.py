"""Build review sheets from actual Paparazzi output; does not alter goldens."""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[3]
FOLDER = ROOT / "shared/compose/src/androidUnitTest/snapshots/images"
OUTPUT = ROOT / "build/brand-review"
OUTPUT.mkdir(parents=True, exist_ok=True)
files = sorted(FOLDER.glob("*.png"))
for offset in range(0,len(files),12):
    sheet = Image.new("RGB", (4*300,3*700), "#D5D8CF")
    draw = ImageDraw.Draw(sheet)
    for index, path in enumerate(files[offset:offset+12]):
        image = Image.open(path).convert("RGB")
        image.thumbnail((290,650))
        row,col = divmod(index,4)
        sheet.paste(image,(col*300+(300-image.width)//2,row*700+40))
        label=path.stem.split("sproutpacepreviewskt.")[-1]
        draw.text((col*300+5,row*700+5),label[:42],fill="black")
        draw.text((col*300+5,row*700+20),label[42:84],fill="black")
    sheet.save(OUTPUT / f"sheet-{offset//12+1}.jpg",quality=92)

# Three production surfaces, light/dark pair for each.
sheet = Image.new("RGB", (6*393, 891), "#D5D8CF")
draw = ImageDraw.Draw(sheet)
for index,(name,theme) in enumerate((
    ("firstrunpreviews.01_first_run","light"),("firstrunpreviews.01_first_run","dark"),
    ("todaypreviews.02_today","light"),("todaypreviews.02_today","dark"),
    ("trayspreviews.03_trays","light"),("trayspreviews.03_trays","dark"),
)):
    path=next(p for p in files if f"{name}_{theme}_" in p.name)
    image=Image.open(path).convert("RGB")
    image.thumbnail((393,851))
    sheet.paste(image,(index*393,40))
    draw.text((index*393+12,12),f"{name.split('.')[1]} / {theme}",fill="black")
sheet.save(ROOT / "docs/design-review/brand-theme-preview.png")
print(f"Prepared {len(files)} golden images for review.")
