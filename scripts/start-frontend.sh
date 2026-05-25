#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd "$SCRIPT_DIR/.." && pwd)

VITE_HOST=${VITE_HOST:-127.0.0.1}
VITE_PORT=${VITE_PORT:-5173}

cd "$REPO_ROOT/gpt-plus-web"

echo "Starting frontend from $PWD"
echo "Using host: $VITE_HOST"
echo "Using port: $VITE_PORT"

exec npm run dev -- --host "$VITE_HOST" --port "$VITE_PORT"
