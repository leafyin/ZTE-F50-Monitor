#!/bin/bash
set -euo pipefail

TARGET="/Applications/F50Monitor.app"
PLIST="$HOME/Library/LaunchAgents/local.f50.monitor.plist"

launchctl unload "$PLIST" 2>/dev/null || true
rm -f "$PLIST"
pkill -f "F50Monitor.app/Contents/MacOS/F50Monitor" 2>/dev/null || true
rm -rf "$TARGET"
echo "==> 已卸载"
