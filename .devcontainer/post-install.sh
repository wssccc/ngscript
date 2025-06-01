#!/usr/bin/env bash
set -euo pipefail

BASH_HISTORY_FILE="${HOME}/.bash_history"
rm -f "$BASH_HISTORY_FILE"

# Persist bash history into the workspace so it survives container rebuilds.
HISTORY_TARGET="${PWD}/.history"
touch "$HISTORY_TARGET"
ln -sf "$HISTORY_TARGET" "$BASH_HISTORY_FILE"
