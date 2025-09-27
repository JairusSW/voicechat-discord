#!/usr/bin/env bash

set -e

cargo build

./copy_natives.sh
