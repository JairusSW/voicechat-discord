#!/usr/bin/env bash
set -euxo pipefail

cargo build

./copy_natives.sh
