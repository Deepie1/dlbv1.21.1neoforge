#!/usr/bin/env python3
"""Create the next cumulative source archive by refreshing the T373 manifest."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'src-modifies-t301-t373.zip'
OUT = ROOT / 'src-modifies-t301-t374.zip'
EXTRA = ['tools/generate_palette_pdf.py']

def main():
    temp = OUT.with_suffix('.zip.tmp')
    with ZipFile(BASE, 'r') as old, ZipFile(temp, 'w', ZIP_DEFLATED, compresslevel=9) as new:
        seen = set()
        for info in old.infolist():
            name = info.filename
            seen.add(name)
            current = ROOT / name
            data = current.read_bytes() if current.is_file() else old.read(name)
            new.writestr(name, data)
        for name in EXTRA:
            if name not in seen:
                new.write(ROOT / name, name, compress_type=ZIP_DEFLATED, compresslevel=9)
    temp.replace(OUT)
    with ZipFile(OUT, 'r') as check:
        bad = check.testzip()
        if bad:
            raise RuntimeError(f'CRC failure: {bad}')
        print(f'Created {OUT.name}: {len(check.namelist())} entries, {OUT.stat().st_size:,} bytes; CRC OK')

if __name__ == '__main__':
    main()
