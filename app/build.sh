#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if [[ -n "${JAVA_HOME:-}" ]]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
: "${SDK:?Set ANDROID_SDK_ROOT to your Android SDK directory}"
BT="$SDK/build-tools/${BUILD_TOOLS_VERSION:-34.0.0}"
AJ="$SDK/platforms/android-34/android.jar"
VERSION=0.6.4
rm -rf build
mkdir -p build/gen build/classes build/dex
"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -o build/base.apk -I "$AJ" --manifest AndroidManifest.xml -A assets --java build/gen build/res.zip \
 --min-sdk-version 26 --target-sdk-version 34 --version-code 12 --version-name "$VERSION"
find src build/gen -name '*.java' > build/sources.txt
javac -encoding UTF-8 -source 8 -target 8 -bootclasspath "$AJ:$BT/core-lambda-stubs.jar" -d build/classes @build/sources.txt
find build/classes -name '*.class' > build/classes.txt
"$BT/d8" --min-api 26 --lib "$AJ" --output build/dex @build/classes.txt
cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q -u ../unsigned.apk classes*.dex)
"$BT/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk
if [[ -n "${WL_KEYSTORE:-}" ]]; then
 : "${WL_KEY_ALIAS:?Set WL_KEY_ALIAS}"
 : "${WL_STORE_PASS:?Set WL_STORE_PASS}"
 export WL_KEY_PASS="${WL_KEY_PASS:-$WL_STORE_PASS}"
 "${BT}/apksigner" sign --ks "$WL_KEYSTORE" --ks-key-alias "$WL_KEY_ALIAS" --ks-pass env:WL_STORE_PASS --key-pass env:WL_KEY_PASS --out "build/WordLock-v${VERSION}.apk" build/aligned.apk
 OUTPUT="build/WordLock-v${VERSION}.apk"
else
 keytool -genkeypair -keystore build/debug.keystore -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -storepass android -keypass android -dname 'CN=Android Debug,O=Android,C=US' >/dev/null 2>&1
 "$BT/apksigner" sign --ks build/debug.keystore --ks-key-alias androiddebugkey --ks-pass pass:android --out "build/WordLock-v${VERSION}-debug.apk" build/aligned.apk
 OUTPUT="build/WordLock-v${VERSION}-debug.apk"
fi
"$BT/apksigner" verify "$OUTPUT"
printf 'Built: %s\n' "$OUTPUT"
