"""Prepare changed golden contact sheets; does not mark them reviewed."""
import hashlib
import json
from pathlib import Path
from PIL import Image, ImageDraw

root = Path(__file__).resolve().parents[1]
images = root / 'shared/compose/src/androidUnitTest/snapshots/images'
out = root / 'build/reports/catalog-v2-visual-review'
out.mkdir(parents=True, exist_ok=True)
baseline = json.loads((root / 'docs/design-review/ux-preview-review.json').read_text(encoding='utf-8-sig'))
before = {entry['file']: entry['sha256'] for entry in baseline['images']}
changed = [p for p in sorted(images.glob('*.png')) if hashlib.sha256(p.read_bytes()).hexdigest() != before.get(p.name)]
manifest = []
for start in range(0, len(changed), 6):
    sheet = Image.new('RGB', (1500, 1900), '#dddddd')
    draw = ImageDraw.Draw(sheet)
    for slot, path in enumerate(changed[start:start + 6]):
        picture = Image.open(path).convert('RGB')
        picture.thumbnail((490, 890))
        x, y = slot % 3 * 500, slot // 3 * 950
        sheet.paste(picture, (x + (500 - picture.width) // 2, y + 48))
        draw.text((x + 4, y + 4), f'{start + slot + 1}: {path.name[:65]}', fill='black')
        manifest.append({'file': path.name, 'beforeSha256': before.get(path.name),
                         'sha256': hashlib.sha256(path.read_bytes()).hexdigest(), 'sheet': start // 6 + 1})
    sheet.save(out / f'sheet-{start // 6 + 1:02d}.png')
(out / 'manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
print(f'{len(changed)} changed captures; {(len(changed) + 5) // 6} sheets: {out}')
