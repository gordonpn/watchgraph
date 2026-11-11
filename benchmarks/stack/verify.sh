#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
.tools/venv/bin/python -m unittest discover -s python -p 'test_*.py'
.tools/venv/bin/python python/audit.py
.tools/venv/bin/python python/measure.py
