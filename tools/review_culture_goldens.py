"""Create review sheets and an exact manifest for this illustration-only delta."""
import hashlib
import json
import subprocess
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
GOLDENS = 'shared/compose/src/androidUnitTest/snapshots/images'
OUT = ROOT / 'docs/assets/culture-review-20261011'
OUT.mkdir(parents=True, exist_ok=True)
baseline = json.loads((ROOT / 'build/culture-start-goldens.json').read_text())
paths = sorted(str(p.relative_to(ROOT)).replace('\\', '/') for p in (ROOT / GOLDENS).glob('*.png')
               if hashlib.sha256(p.read_bytes()).hexdigest() != baseline.get(str(p.relative_to(ROOT)).replace('\\', '/')))
manifest = []
for start in range(0, len(paths), 6):
    sheet = Image.new('RGB', (1500, 1900), '#d8d8d8')
    draw = ImageDraw.Draw(sheet)
    for slot, relative in enumerate(paths[start:start + 6]):
        path = ROOT / relative
        with Image.open(path) as original:
            preview = original.convert('RGB')
            preview.thumbnail((490, 880))
            x, y = slot % 3 * 500, slot // 3 * 950
            sheet.paste(preview, (x + (500 - preview.width) // 2, y + 55))
            label = path.name.split('_snapshot', 1)[-1]
            draw.text((x + 5, y + 5), label[:60], fill='black')
            draw.text((x + 5, y + 23), label[60:120], fill='black')
            manifest.append({'path': relative, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
                             'sheet': start // 6 + 1, 'slot': slot + 1,
                             'width': original.width, 'height': original.height})
    sheet.save(OUT / f'sheet-{start // 6 + 1:02}.png')
(OUT / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
print(f'{len(paths)} changed/new goldens; {(len(paths) + 5) // 6} sheets: {OUT}')
