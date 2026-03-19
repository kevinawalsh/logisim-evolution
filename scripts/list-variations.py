#!/usr/bin/env python3          
import json
import sys

arg = sys.argv[1] if len(sys.argv) > 1 else None
if arg is None:
    print("usage: list-variations.py Wiring:Splitter|Audio:LineSelect|Memory:ROM|...")
    sys.exit(1)
lib,comp = arg.split(':')

d = json.load(open('/tmp/out.json'))

variations = ["facing"] + d[lib][comp]['layout_affecting_attrs']
print(" ".join([f"{attr:<12}" for attr in variations]))

layouts = d[lib][comp]['port_layouts']
print('-' * (13*len(variations)-1))
for L in layouts:
    s = " ".join([f"{L.get(attr, '-'):<12}" for attr in variations])
    extras = {k: v for k, v in L.items() if k not in variations and k != 'ports'}
    if extras:
        s += "  " + " ".join(f"{k}={v}" for k, v in extras.items())
    print(s)
