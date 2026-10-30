#!/usr/bin/env python3
"""Diff the Java port against the TypeScript oracle.

    compare.py elements <ts.elements.json> <java.elements.json>
    compare.py blocks   <ts.blocks.txt[.gz]> <java.blocks.txt[.gz]>

Elements are matched by id; every field is compared with numbers to 1e-9. Blocks are compared
voxel by voxel ("x y z state" lines). Prints a summary and the first differences; exit 1 on any.
"""
import gzip, json, sys
from collections import Counter

def opener(p):
    return gzip.open(p, 'rt') if p.endswith('.gz') else open(p)

def same(a, b, path, out):
    if isinstance(a, (int, float)) and isinstance(b, (int, float)) and not isinstance(a, bool) and not isinstance(b, bool):
        if abs(a - b) > 1e-9: out.append(f'{path}: {a} != {b}')
    elif isinstance(a, dict) and isinstance(b, dict):
        for k in sorted(set(a) | set(b)):
            if k not in a or k not in b: out.append(f'{path}.{k}: missing on {"ts" if k not in a else "java"} side')
            else: same(a[k], b[k], f'{path}.{k}', out)
    elif isinstance(a, list) and isinstance(b, list):
        if len(a) != len(b): out.append(f'{path}: length {len(a)} != {len(b)}'); return
        for i, (x, y) in enumerate(zip(a, b)): same(x, y, f'{path}[{i}]', out)
    elif a != b:
        out.append(f'{path}: {a!r} != {b!r}')

def elements(ts_path, java_path):
    ts = {e['id']: e for e in json.load(opener(ts_path))['elements']}
    jv = {e['id']: e for e in json.load(opener(java_path))['elements']}
    only_ts = sorted(set(ts) - set(jv)); only_java = sorted(set(jv) - set(ts))
    diffs = []
    for i in sorted(set(ts) & set(jv)):
        out = []
        same(ts[i], jv[i], i, out)
        if out: diffs.append(out)
    print(f'elements: ts {len(ts)}, java {len(jv)}, only-ts {len(only_ts)}, only-java {len(only_java)}, differing {len(diffs)}')
    for i in only_ts[:15]: print('  only ts  :', i)
    for i in only_java[:15]: print('  only java:', i)
    for d in diffs[:15]: print('  ' + '\n    '.join(d[:4]))
    return not (only_ts or only_java or diffs)

def blocks(ts_path, java_path):
    def load(p):
        m = {}
        for line in opener(p):
            x, y, z, s = line.rstrip('\n').split(' ', 3)
            m[(int(x), int(y), int(z))] = s
        return m
    ts, jv = load(ts_path), load(java_path)
    keys = set(ts) | set(jv)
    bad = [(k, ts.get(k, 'air'), jv.get(k, 'air')) for k in keys if ts.get(k) != jv.get(k)]
    print(f'blocks: ts {len(ts)}, java {len(jv)}, differing {len(bad)}')
    pairs = Counter((a, b) for _, a, b in bad)
    for (a, b), n in pairs.most_common(15): print(f'  {n:7d}  ts {a}  java {b}')
    for k, a, b in sorted(bad)[:10]: print('  at', k, a, '|', b)
    return not bad

if __name__ == '__main__':
    mode, a, b = sys.argv[1:4]
    ok = elements(a, b) if mode == 'elements' else blocks(a, b)
    sys.exit(0 if ok else 1)
