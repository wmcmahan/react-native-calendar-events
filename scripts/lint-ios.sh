#!/usr/bin/env bash
# swift-format (bundled with Xcode) in strict mode: any finding fails.
# Use `yarn format:ios` to fix formatting in place.
set -euo pipefail

cd "$(dirname "$0")/.."
xcrun swift-format lint --strict --recursive ios
