#!/bin/sh
set -eu

if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi

echo "ERROR: 'gradle' command not found in PATH. Please install Gradle 8.6+ or restore gradle-wrapper.jar." >&2
exit 1
