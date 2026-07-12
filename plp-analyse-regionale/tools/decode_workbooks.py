#!/usr/bin/env python3
from __future__ import annotations
import base64
import hashlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "workbooks" / "base64"
TARGET = ROOT / "workbooks" / "decoded"
TARGET.mkdir(parents=True, exist_ok=True)

for source in sorted(SOURCE.glob("*.xlsm.b64")):
    target = TARGET / source.name.removesuffix(".b64")
    encoded = "".join(source.read_text(encoding="ascii").split())
    data = base64.b64decode(encoded, validate=True)
    target.write_bytes(data)
    digest = hashlib.sha256(data).hexdigest()
    print(f"{target}  sha256={digest}")
