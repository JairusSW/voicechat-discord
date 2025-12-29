#!/usr/bin/env bash
set -euo pipefail

echo Making venv
python3 -m venv venv

echo Sourcing venv
source venv/bin/activate

echo Installing dependencies
python3 -m pip install jinja2
