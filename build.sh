#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"

BIN_NAME="F50Monitor"
APP="build/$BIN_NAME.app"
arch=$(uname -m)

echo "==> 清理旧产物"
rm -rf "$APP"
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources"

echo "==> 编译 arch=$arch"
swiftc -O -parse-as-library \
  -target "${arch}-apple-macos13.0" \
  -framework SwiftUI -framework AppKit \
  -o "$APP/Contents/MacOS/$BIN_NAME" \
  Sources/*.swift

cp Resources/Info.plist "$APP/Contents/Info.plist"
if [ -f Resources/AppIcon.icns ]; then
  cp Resources/AppIcon.icns "$APP/Contents/Resources/"
fi

echo "==> 临时签名"
codesign --force --deep --sign - "$APP" >/dev/null 2>&1 || true

echo "==> 完成：$(pwd)/$APP"
