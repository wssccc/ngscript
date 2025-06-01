#!/usr/bin/env bash
#
# Fetch the TC39 test262 conformance test suite into ./test262
# (which is git-ignored, so the suite is not vendored in the repository).
#
# Usage:
#   tools/fetch-test262.sh            # fetch default branch (main)
#   tools/fetch-test262.sh es2025     # fetch a branch or tag by name
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TEST262_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)/test262"
REF="${1:-main}"
TMP_TGZ="${TEST262_DIR}.tmp.tgz"

echo "Fetching test262 (${REF}) ..."

if ! curl -fL --retry 3 "https://github.com/tc39/test262/archive/refs/heads/${REF}.tar.gz" -o "${TMP_TGZ}" 2>/dev/null; then
    echo "Branch '${REF}' not found, trying as tag ..."
    curl -fL --retry 3 "https://github.com/tc39/test262/archive/refs/tags/${REF}.tar.gz" -o "${TMP_TGZ}"
fi

rm -rf "${TEST262_DIR}"
mkdir -p "${TEST262_DIR}"
tar -xzf "${TMP_TGZ}" -C "${TEST262_DIR}" --strip-components=1
rm -f "${TMP_TGZ}"

echo "test262 fetched to ${TEST262_DIR}"
