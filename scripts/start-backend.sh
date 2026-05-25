#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd "$SCRIPT_DIR/.." && pwd)

SPRING_PROFILE=${SPRING_PROFILE:-local}

cd "$REPO_ROOT/gpt-plus-core"

echo "Starting backend from $PWD"
echo "Using Spring profile: $SPRING_PROFILE"

exec jenv exec mvn spring-boot:run -Dspring-boot.run.profiles="$SPRING_PROFILE"
