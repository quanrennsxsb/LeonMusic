#!/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
DERIVED_DATA="${DERIVED_DATA:-/tmp/LeonMusicUnifiedDerivedData}"
OUTPUT_DIR="$ROOT_DIR/build/macos/unified"
APP_NAME="LeonMusic.app"
ASSEMBLED_APP="$OUTPUT_DIR/$APP_NAME"
COMPOSE_APP="$ROOT_DIR/composeApp/build/compose/binaries/main/app/$APP_NAME"
XCODE_APP="$DERIVED_DATA/Build/Products/Release/$APP_NAME"
RUNTIME_LIB_DIR="$ASSEMBLED_APP/Contents/runtime/Contents/Home/lib"
SIGN_IDENTITY="${SIGN_IDENTITY:-Apple Development: 14409594@qq.com (FX4M9L4556)}"

JAVA_21_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
if [ -z "$JAVA_21_HOME" ] && [ -x "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home/bin/java" ]; then
  JAVA_21_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
fi
if [ -n "$JAVA_21_HOME" ]; then
  export JAVA_HOME="$JAVA_21_HOME"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

cd "$ROOT_DIR"
./gradlew :composeApp:createDistributable
xcodebuild \
  -project "$ROOT_DIR/macosApp/macosApp.xcodeproj" \
  -scheme macosApp \
  -configuration Release \
  -derivedDataPath "$DERIVED_DATA" \
  -allowProvisioningUpdates \
  build

rm -rf "$ASSEMBLED_APP"
mkdir -p "$OUTPUT_DIR"
/usr/bin/ditto "$COMPOSE_APP" "$ASSEMBLED_APP"
/usr/libexec/PlistBuddy -c "Delete :CFBundleURLTypes" "$ASSEMBLED_APP/Contents/Info.plist" 2>/dev/null || true
/usr/libexec/PlistBuddy -c "Add :CFBundleURLTypes array" "$ASSEMBLED_APP/Contents/Info.plist"
/usr/libexec/PlistBuddy -c "Add :CFBundleURLTypes:0 dict" "$ASSEMBLED_APP/Contents/Info.plist"
/usr/libexec/PlistBuddy -c "Add :CFBundleURLTypes:0:CFBundleURLName string top.iwesley.lyn.music" "$ASSEMBLED_APP/Contents/Info.plist"
/usr/libexec/PlistBuddy -c "Add :CFBundleURLTypes:0:CFBundleURLSchemes array" "$ASSEMBLED_APP/Contents/Info.plist"
/usr/libexec/PlistBuddy -c "Add :CFBundleURLTypes:0:CFBundleURLSchemes:0 string leonmusic" "$ASSEMBLED_APP/Contents/Info.plist"
mkdir -p "$ASSEMBLED_APP/Contents/PlugIns"
/usr/bin/ditto "$XCODE_APP/Contents/PlugIns/LeonMusicWidget.appex" "$ASSEMBLED_APP/Contents/PlugIns/LeonMusicWidget.appex"
if [ ! -f "$XCODE_APP/Contents/embedded.provisionprofile" ]; then
  echo "Required macOS app provisioning profile not found at $XCODE_APP/Contents/embedded.provisionprofile"
  exit 1
fi
/usr/bin/ditto "$XCODE_APP/Contents/embedded.provisionprofile" "$ASSEMBLED_APP/Contents/embedded.provisionprofile"

copy_dylib() {
  local source="$1"
  local target="$RUNTIME_LIB_DIR/$(basename "$source")"
  /usr/bin/ditto "$source" "$target"
  chmod u+w "$target"
  install_name_tool -id "@rpath/$(basename "$source")" "$target"
}

copy_dylib /opt/homebrew/opt/harfbuzz/lib/libharfbuzz.0.dylib
copy_dylib /opt/homebrew/opt/freetype/lib/libfreetype.6.dylib
copy_dylib /opt/homebrew/opt/glib/lib/libglib-2.0.0.dylib
copy_dylib /opt/homebrew/opt/graphite2/lib/libgraphite2.3.dylib
copy_dylib /opt/homebrew/opt/libpng/lib/libpng16.16.dylib
copy_dylib /opt/homebrew/opt/gettext/lib/libintl.8.dylib
copy_dylib /opt/homebrew/opt/pcre2/lib/libpcre2-8.0.dylib

install_name_tool -change /opt/homebrew/opt/harfbuzz/lib/libharfbuzz.0.dylib @rpath/libharfbuzz.0.dylib "$RUNTIME_LIB_DIR/libfontmanager.dylib"
install_name_tool -change /opt/homebrew/opt/freetype/lib/libfreetype.6.dylib @rpath/libfreetype.6.dylib "$RUNTIME_LIB_DIR/libfontmanager.dylib"
install_name_tool -change /opt/homebrew/opt/freetype/lib/libfreetype.6.dylib @rpath/libfreetype.6.dylib "$RUNTIME_LIB_DIR/libharfbuzz.0.dylib"
install_name_tool -change /opt/homebrew/opt/glib/lib/libglib-2.0.0.dylib @rpath/libglib-2.0.0.dylib "$RUNTIME_LIB_DIR/libharfbuzz.0.dylib"
install_name_tool -change /opt/homebrew/opt/graphite2/lib/libgraphite2.3.dylib @rpath/libgraphite2.3.dylib "$RUNTIME_LIB_DIR/libharfbuzz.0.dylib"
install_name_tool -change /opt/homebrew/opt/libpng/lib/libpng16.16.dylib @rpath/libpng16.16.dylib "$RUNTIME_LIB_DIR/libfreetype.6.dylib"
install_name_tool -change /opt/homebrew/opt/gettext/lib/libintl.8.dylib @rpath/libintl.8.dylib "$RUNTIME_LIB_DIR/libglib-2.0.0.dylib"
install_name_tool -change /opt/homebrew/opt/pcre2/lib/libpcre2-8.0.dylib @rpath/libpcre2-8.0.dylib "$RUNTIME_LIB_DIR/libglib-2.0.0.dylib"

sign_macho_files() {
  local directory="$1"
  while IFS= read -r file_path; do
    if file "$file_path" | grep -q "Mach-O"; then
      codesign --force --sign "$SIGN_IDENTITY" --options runtime "$file_path"
    fi
  done < <(find "$directory" -type f)
}

sign_macho_files "$ASSEMBLED_APP/Contents/runtime"
sign_macho_files "$ASSEMBLED_APP/Contents/app"
codesign --force --sign "$SIGN_IDENTITY" --options runtime --entitlements "$ROOT_DIR/macosApp/LeonMusicWidget/LeonMusicWidget.entitlements" "$ASSEMBLED_APP/Contents/PlugIns/LeonMusicWidget.appex"
codesign --force --sign "$SIGN_IDENTITY" --options runtime --entitlements "$ROOT_DIR/macosApp/macosApp/LeonMusicPlayer.entitlements" "$ASSEMBLED_APP"
codesign --verify --deep --strict --verbose=2 "$ASSEMBLED_APP"

echo "Unified app written to $ASSEMBLED_APP"
