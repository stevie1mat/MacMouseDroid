#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
SDK="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"
BT="$SDK/build-tools/36.0.0"
export JAVA_HOME="${MOUSE_JAVA_HOME:-/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home}"
export PATH="$JAVA_HOME/bin:$PATH"
mkdir -p build/classes build/dex dist
"$BT/aapt2" compile --dir android/res -o build/resources.zip
"$BT/aapt2" link -o build/base.apk --manifest android/AndroidManifest.xml -I "$SDK/platforms/android-35/android.jar" --java build android/../build/resources.zip
javac -source 8 -target 8 -bootclasspath "$SDK/platforms/android-35/android.jar:$BT/core-lambda-stubs.jar" -d build/classes android/src/com/mouseformobile/*.java
jar cf build/classes.jar -C build/classes .
"$BT/d8" --lib "$SDK/platforms/android-35/android.jar" --min-api 26 --output build/dex build/classes.jar
cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q -u ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 build/unsigned.apk build/aligned.apk
if [ ! -f build/debug.keystore ]; then
  keytool -genkeypair -keystore build/debug.keystore -storepass android -keypass android -alias androiddebugkey -dname "CN=Local Development" -keyalg RSA -validity 10000
fi
"$BT/apksigner" sign --ks build/debug.keystore --ks-pass pass:android --out dist/MouseForMobile.apk build/aligned.apk
"$BT/apksigner" verify dist/MouseForMobile.apk
mkdir -p "dist/Mouse for Mobile.app/Contents/MacOS" build/swift-cache
swiftc mac/MouseForMobile.swift -module-cache-path build/swift-cache -o "dist/Mouse for Mobile.app/Contents/MacOS/MouseForMobile" -framework AppKit
cp mac/Info.plist "dist/Mouse for Mobile.app/Contents/Info.plist"
codesign --force --sign - "dist/Mouse for Mobile.app"
