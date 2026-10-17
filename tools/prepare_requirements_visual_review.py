"""Prepare current production goldens for manual inspection; never approves hashes."""
import hashlib
import json
import re
import shutil
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
GOLDENS = ROOT / 'shared/compose/src/androidUnitTest/snapshots/images'
OUT = ROOT / 'build/reports/requirements-fixes-20261017/visual'
OUT.mkdir(parents=True, exist_ok=True)
catalog = json.loads((ROOT / 'shared/compose/build/reports/paparazzi/preview-coverage.json').read_text())
expected = {f'p_P_snapshot[{index}]_{case["id"].lower()}.png' for index, case in enumerate(catalog['cases'])}
if '--prune-obsolete' in sys.argv:
    # Additional cases renumber existing parameter indices. Preserve every case and archive
    # only old filenames whose same stable preview ID has a current recorded image.
    obsolete = [path for path in GOLDENS.glob('*.png') if path.name not in expected]
    stable_tails = {name.split(']_', 1)[1] for name in expected}
    assert all((GOLDENS / name).is_file() for name in expected), 'Record every current case first'
    assert GOLDENS.resolve().is_relative_to(ROOT)
    archived = []
    for path in obsolete:
        assert path.resolve().parent == GOLDENS.resolve()
        assert path.name.split(']_', 1)[1] in stable_tails, f'Preview disappeared: {path.name}'
        destination = OUT / 'obsolete-baseline' / path.name
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, destination)
        archived.append({'file': path.name, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()})
        path.unlink()
    (OUT / 'obsolete-filenames.json').write_text(json.dumps(archived, indent=2) + '\n', encoding='utf-8')
    print(f'Archived {len(archived)} obsolete parameter filenames; every stable preview retained')
previous = json.loads((ROOT / 'docs/design-review/ux-preview-review.json').read_text(encoding='utf-8-sig'))
reviewed = {item['file']: item['sha256'] for item in previous['images']}
def image_order(path):
    parameter = re.search(r'snapshot\[(\d+)\]', path.name)
    return (0, int(parameter.group(1))) if parameter else (1, path.name)

paths = sorted(GOLDENS.glob('*.png'), key=image_order)
manifest = []
for start in range(0, len(paths), 6):
    sheet = Image.new('RGB', (1500, 1900), '#d8d8d8')
    draw = ImageDraw.Draw(sheet)
    for slot, path in enumerate(paths[start:start + 6]):
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        with Image.open(path) as original:
            preview = original.convert('RGB')
            preview.thumbnail((490, 880))
            x, y = slot % 3 * 500, slot // 3 * 950
            sheet.paste(preview, (x + (500 - preview.width) // 2, y + 55))
            label = path.name.split('_snapshot', 1)[-1]
            draw.text((x + 5, y + 5), label[:64], fill='black')
            draw.text((x + 5, y + 23), label[64:128], fill='black')
            manifest.append({'file': path.name, 'sha256': digest,
                             'sheet': start // 6 + 1, 'slot': slot + 1,
                             'changedSinceApproval': reviewed.get(path.name) != digest,
                             'width': original.width, 'height': original.height})
    sheet.save(OUT / f'sheet-{start // 6 + 1:03}.png')
(OUT / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
print(f'{len(paths)} images; {(len(paths) + 5) // 6} inspection sheets; '
      f'{sum(row["changedSinceApproval"] for row in manifest)} need current review: {OUT}')
