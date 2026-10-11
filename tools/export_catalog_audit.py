"""Export the current, deliberately narrow Kotlin catalog format for external review.

Run from any directory: python tools/export_catalog_audit.py
This is a review snapshot, not an application import/backup format.
"""
import hashlib
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs/catalog-audit/2026-10-11/v2'
DOMAIN = 'shared/domain/src/commonMain/kotlin/com/sedsoftware/sproutpace/domain/'
COMPOSE = 'shared/compose/src/commonMain/'
catalog_path = DOMAIN + 'catalog/SproutPaceCatalog.kt'
alias_path = COMPOSE + 'kotlin/com/sedsoftware/sproutpace/compose/LocalizedText.kt'
catalog = (ROOT / catalog_path).read_text(encoding='utf-8-sig')
aliases = (ROOT / alias_path).read_text(encoding='utf-8-sig')
direct = dict(re.findall(r'"([\w]+)" to Res.string.(\w+)', aliases))
suffixes = dict(re.findall(r'"(\w+)" -> Res.string.(\w+)', aliases))
resources = {}
inputs = [catalog_path, alias_path, DOMAIN + 'model/Recipe.kt', DOMAIN + 'engine/MeasurementEngine.kt', DOMAIN + 'engine/BatchPlanningEngine.kt']
for lang, folder in [('en', 'values'), ('ru', 'values-ru')]:
    resources[lang] = {}
    for path in sorted((ROOT / COMPOSE / 'composeResources' / folder).glob('*.xml')):
        inputs.append(path.relative_to(ROOT).as_posix())
        for element in ET.parse(path).getroot().findall('string'):
            resources[lang][element.attrib['name']] = ''.join(element.itertext())

def text(key):
    resolved = key
    if key not in resources['en']:
        resolved = direct.get(key)
        if resolved is None:
            resolved = next((v for k, v in suffixes.items() if key.endswith('_' + k)), None)
    assert resolved and all(resolved in r for r in resources.values()), key
    return {'key': key, 'resolvedResourceKey': resolved, **{lang: r[resolved] for lang, r in resources.items()}}

version = int(re.search(r'const val VERSION: Int = (\d+)', catalog)[1])
date = re.search(r'LocalDate\((\d+), (\d+), (\d+)\)', catalog)
reviewed = '-'.join(f'{int(n):02d}' for n in date.groups())
sources = []
for name, url, title in re.findall(r'private val (\w+) = SourceMetadata\(\s*url = "([^"]+)",\s*titleKey = LocalizationKey\("([^"]+)"\)', catalog):
    sources.append({'id': name, 'url': url, 'title': text(title), 'recordedReviewedOn': reviewed})
assert len(sources) == 3
pattern = r'culture\("(\w+)", ([\d.]+), (\w+), (\w+), (null|\d+\.\.\d+), (\d+)\.\.(\d+), (\d+)\.\.(\d+)\)'
areas = {k: float(v) for k, v in re.findall(r'private const val (\w+) = ([\d.]+)', catalog)}
rows = re.findall(pattern, catalog)
assert len(rows) == catalog.count('        culture("') == 8
entries = []
for cid, grams, area, source, soak, bmin, bmax, hmin, hmax in rows:
    grams = float(grams)
    density = grams / areas[area]
    bmin, bmax, hmin, hmax = map(int, (bmin, bmax, hmin, hmax))
    lmin, lmax = hmin - bmax, hmax - bmax
    stages = []
    def stage(name, minimum, maximum, unit, suffix):
        stages.append({'stage': name, 'duration': {'min': minimum, 'max': maximum, 'unit': unit}, 'guidance': text(f'culture_{cid}_v2_{suffix}')})
    if soak != 'null':
        low, high = map(int, soak.split('..'))
        stage('SOAKING', low, high, 'hours', 'soaking')
    stage('SOWN', 0, 0, 'hours', 'sowing')
    stage('BLACKOUT_OR_WEIGHT', bmax, bmax, 'days', 'blackout')
    stage('LIGHT_GROWTH', lmin, lmax, 'days', 'light_growth')
    stage('READY_FOR_HARVEST', 0, 0, 'hours', 'harvest')
    entries.append({'id': cid, 'name': text(f'culture_{cid}_name'), 'scientificName': text(f'culture_{cid}_scientific_name'),
        'seedDensity': {'value': density, 'unit': 'g/cm²', 'version': version, 'sourceId': source,
            'numericalDerivation': {'seedGrams': grams, 'areaCm2': areas[area], 'formula': 'seedGrams / areaCm2', 'germinationAdjustment': 'none',
                'assumption': 'nominal 10 x 20 inches, not measured internal area' if source == 'utahState' else 'explicit 25 x 50 cm; mustard selects lower 10 g endpoint'}},
        'sourceTiming': {'coveredDays': [bmin, bmax], 'sowToHarvestDays': [hmin, hmax], 'plannedCoveredDays': bmax},
        'stages': stages, 'substrateNote': text(f'culture_{cid}_v2_substrate_note'), 'lightingNote': text(f'culture_{cid}_v2_lighting_note'),
        'safety': [text(k) for k in re.findall(r'LocalizationKey\("(safety_\w+)"\)', catalog)],
        'sourceIds': [source], 'illustrationResourceName': f'culture_{cid}_cover',
        'derivedChecks': {'nominal1020AreaCm2': 1290.32, 'seedGramsForNominal1020': round(density * 1290.32, 5),
            'sowToHarvestDurationDays': {'min': bmax + lmin, 'max': bmax + lmax}, 'plannedDurationDays': bmax + lmax}})
assert len({e['id'] for e in entries}) == 8
inputs += ['spec/app-spec.json', 'spec/product.md', 'spec/data.md', 'spec/domain.md', 'spec/quality.md', 'spec/flows/FLOW-003.md', 'spec/screens/SCREEN-008.md']
payload = {'schemaVersion': 1, 'snapshotDate': '2026-10-11', 'gitHead': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
    'catalogVersion': version, 'recordedReviewedOn': reviewed,
    'purpose': 'Independent content review; not an import format, source substantiation or physical grow validation.',
    'semantics': {'seedMass': 'areaCm2 * densityGPerCm2; no germination adjustment in MeasurementEngine',
        'durations': 'Covered allowance = source covered maximum; light = total harvest endpoints minus allowance. This is an app planning allocation, not a source-measured light interval. Original covered ranges remain in guidance.',
        'planning': 'BatchPlanningEngine sums stage maxima; derived range sums minima/maxima and excludes presoak.',
        '1020': 'Comparison assumption only: nominal 10 x 20 inches = 1290.32 cm²; actual internal growing area may differ.',
        'missingSoak': 'Stage absent in app, not a scientific proof of prohibition.',
        'weight': 'BLACKOUT_OR_WEIGHT combines concepts. Source-specific localized guidance describes weight where applicable; weighted/unweighted transitions are not separate scheduled stages.'},
    'sources': sources, 'cultures': entries,
    'inputSha256': {p: hashlib.sha256((ROOT / p).read_bytes()).hexdigest() for p in inputs},
    'specContext': {p: (ROOT / p).read_text(encoding='utf-8-sig') for p in inputs if p.startswith('spec/') and p != 'spec/app-spec.json'},
    'implementationContext': {p: (ROOT / p).read_text(encoding='utf-8-sig') for p in inputs if p.endswith('.kt')}}
OUT.mkdir(parents=True, exist_ok=True)
output = OUT / 'catalog-review.json'
output.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
assert json.loads(output.read_text(encoding='utf-8')) == payload
print(f'Exported {len(entries)} cultures, {len(sources)} sources; bilingual keys resolved; JSON round-trip passed: {output}')
