#!/usr/bin/env python3
# /// script
# requires-python = ">=3.10"
# dependencies = ["pillow"]
# ///
"""Export five finished poles from the installed debug lab, then use the asset importer.

uv run tools/export_territory_poles.py <drop> --adb <adb.exe> [--out <resources>]
The default isolated package is com.daengs.app.cardpreview; build/install it first.
Only that debug package is restarted. No location/claim/photo APIs are used.
"""
import argparse
from pathlib import Path
import subprocess
from PIL import Image
from import_room_assets import main as import_assets

STYLES = ('neutral', 'mine_unverified', 'mine_verified', 'other_unverified', 'other_verified')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('drop', type=Path)
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--out', type=Path, default=Path('app/src/main/res/drawable-nodpi'))
    args = parser.parse_args()
    package = 'com.daengs.app.cardpreview'
    # Fail before launch if this is not a debuggable installation.
    subprocess.run([args.adb, 'shell', 'run-as', package, 'id'], check=True)
    # Delete only this generator's five debug outputs; stale exports must never look successful.
    subprocess.run([args.adb, 'shell', 'run-as', package, 'rm', '-f',
                    *[f'files/territory_pole_{style}.png' for style in STYLES]], check=True)
    subprocess.run([args.adb, 'shell', 'am', 'start', '-S', '-W', '-n',
                    f'{package}/com.daengs.app.ui.walk.TerritoryPoleLabActivity',
                    '--ez', 'measureIcons', 'true', '--ez', 'legacyIcons', 'true',
                    '--ez', 'exportIcons', 'true'], check=True)
    target = args.drop / 'map_prepared'
    target.mkdir(parents=True, exist_ok=True)
    for style in STYLES:
        name = f'territory_pole_{style}.png'
        data = subprocess.check_output([args.adb, 'exec-out', 'run-as', package, 'cat', f'files/{name}'])
        (target / name).write_bytes(data)
    import_assets(args.drop, args.out)
    # Lossless means visible RGB and every alpha survive. Fully transparent RGB is irrelevant.
    for style in STYLES:
        source = Image.open(target / f'territory_pole_{style}.png').convert('RGBA')
        result = Image.open(args.out / f'map_territory_pole_{style}_ready.webp').convert('RGBA')
        if source.size != result.size:
            raise ValueError(f'{style}: canvas changed')
        for before, after in zip(source.getdata(), result.getdata()):
            if before[3] != after[3] or (before[3] and before != after):
                raise ValueError(f'{style}: visible pixels changed')
    print('5 styles: canvas, alpha and visible pixels preserved')


if __name__ == '__main__':
    main()
