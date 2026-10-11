"""Refresh evidence only after manually reviewing the illustration golden delta."""
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REVIEW = 'docs/assets/illustration-review-20261011'


def reference(path):
    return {'path': path, 'sha256': hashlib.sha256((ROOT / path).read_bytes()).hexdigest()}


spec = json.loads((ROOT / 'spec/app-spec.json').read_text(encoding='utf-8-sig'))
requirements = {a['id']: a for a in spec['assetRequirements']['items']}
manifest_path = ROOT / 'docs/assets/asset-manifest.json'
manifest = json.loads(manifest_path.read_text(encoding='utf-8-sig'))
review = json.loads((ROOT / REVIEW / 'manifest.json').read_text(encoding='utf-8'))
reviewed = {row['path'] for row in review}
for asset in manifest['assets']:
    # Other assets share production screenshots; only refresh hashes for reviewed captures.
    for evidence in asset.get('visualEvidence', []):
        if evidence['path'] in reviewed:
            evidence.update(reference(evidence['path']))
    for usage in asset.get('usages', []):
        if 'sha256' in usage:
            usage.update(reference(usage['path']))
    if asset['id'] not in {f'ASSET-{i:03}' for i in range(16, 22)}:
        continue
    asset['outputs'] = [reference(v['path']) for v in requirements[asset['id']]['variants']]
    asset['provenance'] = {
        'method': 'generate-raster',
        'source': 'User-approved imagegen Material-flat PNG candidates; '
                  'docs/assets/illustration-candidates-20261011/material-flat',
        'rights': 'Generated artwork approved by the user; '
                  'no independent legal clearance asserted.',
        'generationRecord': reference('docs/assets/generation/material-flat-illustrations.json'),
    }
    if asset['id'] == 'ASSET-016':
        captures = [r['path'] for r in review if '.soakingscenario.' in r['path']]
    elif asset['id'] == 'ASSET-021':
        captures = [r['path'] for r in review if any(s in r['path'] for s in
                    ('.emptytodaypreview.', '.emptytrayspreview.', '.emptyplanpreview.'))]
    else:
        captures = [r['path'] for r in review if any(s in r['path'] for s in
                    ('.recipeassetinspectionpreview.', '.recipeartworkdark.'))]
    asset['visualEvidence'] = [reference(p) for p in captures] + [reference(REVIEW + '/review.md')]
    asset['visualReview'] = ('2026-10-11: reviewed final production goldens in both themes, '
                            'including small stage art, empty states, RU/200% and adaptive consumers. '
                            'Original approved PNG shapes retained, without tint or vector conversion. '
                            'See bounded review for transparency and contrast findings; '
                            'not a full-app release declaration.')
manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print('Updated ASSET-016..021 and shared reviewed screenshot hashes')
