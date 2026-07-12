#!/usr/bin/env python3
"""Extrait les modules VBA d’un classeur XLSM avec oletools."""
from pathlib import Path
import sys
from oletools.olevba import VBA_Parser

if len(sys.argv) != 3:
    raise SystemExit("Usage: python extract_vba.py fichier.xlsm dossier_sortie")

source = Path(sys.argv[1])
out = Path(sys.argv[2])
out.mkdir(parents=True, exist_ok=True)
parser = VBA_Parser(str(source))
try:
    if not parser.detect_vba_macros():
        raise SystemExit("Aucune macro VBA détectée.")
    for _, _, vba_filename, code in parser.extract_macros():
        target = out / Path(vba_filename).name
        target.write_text(code, encoding="utf-8", errors="replace")
        print(target)
finally:
    parser.close()
