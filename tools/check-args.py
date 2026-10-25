#!/usr/bin/env python3
"""Runs the real Aether core with every command line the app's unit tests exported and fails
if the core rejects one: an option it does not know or a value it cannot read.

A run that is still working after a few seconds (registering, scanning) took its options.
Usage: tools/check-args.py <aether binary> <core-args.jsonl>
"""
import json
import os
import subprocess
import sys
import tempfile

REJECTED = ("unknown option", "is not an address", "is not a", "invalid", "expects a value", "missing value", "must be")

binary, path = sys.argv[1], sys.argv[2]
bad = 0
with open(path) as f:
    lines = [json.loads(l) for l in f if l.strip()]

for args in lines:
    with tempfile.TemporaryDirectory() as tmp:
        env = dict(os.environ, HOME=tmp, TMPDIR=tmp)
        try:
            r = subprocess.run([binary, *args], cwd=tmp, env=env, stdin=subprocess.DEVNULL,
                               capture_output=True, text=True, timeout=4)
            out = (r.stdout + r.stderr).lower()
            head = "\n".join(out.splitlines()[:6])
            if any(m in head for m in REJECTED):
                print(f"REJECTED {' '.join(args)}\n{head}\n")
                bad += 1
            else:
                print(f"ended early (code {r.returncode}) but options accepted: {' '.join(args)}")
        except subprocess.TimeoutExpired as e:
            out = ((e.stdout or b"").decode(errors="replace") if isinstance(e.stdout, bytes) else (e.stdout or "")).lower()
            if any(m in "\n".join(out.splitlines()[:6]) for m in REJECTED):
                print(f"REJECTED {' '.join(args)}\n{out[:600]}\n")
                bad += 1
            else:
                print(f"ok: {' '.join(args)}")

print(f"\n{len(lines) - bad}/{len(lines)} command lines accepted")
sys.exit(1 if bad else 0)
