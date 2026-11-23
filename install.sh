#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"

APP="build/F50Monitor.app"
TARGET="/Applications/F50Monitor.app"
PLIST="$HOME/Library/LaunchAgents/local.f50.monitor.plist"

if [ ! -d "$APP" ]; then
  echo "找不到构建产物，先跑 ./build.sh"
  exit 1
fi

echo "==> 安装到 /Applications"
rm -rf "$TARGET"
cp -R "$APP" "$TARGET"

echo "==> 写入开机自启"
mkdir -p "$(dirname "$PLIST")"
cat > "$PLIST" <<PLISTEOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>Label</key>
	<string>local.f50.monitor</string>
	<key>ProgramArguments</key>
	<array>
		<string>$TARGET/Contents/MacOS/F50Monitor</string>
	</array>
	<key>RunAtLoad</key>
	<true/>
	<key>KeepAlive</key>
	<false/>
</dict>
</plist>
PLISTEOF

launchctl unload "$PLIST" 2>/dev/null || true
launchctl load "$PLIST"

echo "==> 已安装并启动：$TARGET"
