#!/bin/bash
set -euo pipefail

# Offline build against an installed Android Studio; no downloads or user IDE changes.
cd "$(dirname "$0")/.."
ROOT="$PWD"
IDE="${ANDROID_STUDIO_HOME:-$HOME/Applications/Android Studio.app/Contents}"
JDK="$IDE/jbr/Contents/Home"
KOTLIN="$IDE/plugins/Kotlin/kotlinc/lib"
if [[ ! -f "$KOTLIN/kotlin-compiler.jar" ]]; then
  echo "Set ANDROID_STUDIO_HOME to the Android Studio Contents directory." >&2
  exit 1
fi
CP=""
for jar in "$IDE"/lib/*.jar "$IDE"/plugins/textmate-plugin/lib/*.jar "$IDE"/plugins/textmate-plugin/lib/modules/*.jar; do
  CP="${CP:+$CP:}$jar"
done
mkdir -p "$ROOT/build/distributions"
rm -rf "$ROOT/build/local" "$ROOT/build/staging"
mkdir -p "$ROOT/build/local/classes" "$ROOT/build/local/test-classes" "$ROOT/build/staging/fastlane-navigator/lib"
"$JDK/bin/java" -cp "$KOTLIN/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -no-reflect -jvm-target 21 -jdk-home "$JDK" -classpath "$CP" \
  -d "$ROOT/build/local/classes" "$ROOT/src/main/kotlin"
"$JDK/bin/jar" --create --file "$ROOT/build/staging/fastlane-navigator/lib/fastlane-navigator.jar" \
  -C "$ROOT/build/local/classes" . -C "$ROOT/src/main/resources" .
cp -R "$ROOT/bundles" "$ROOT/build/staging/fastlane-navigator/"
cp -R "$ROOT/formatter" "$ROOT/build/staging/fastlane-navigator/"
cd "$ROOT/build/staging"
rm -f "$ROOT/build/distributions/fastlane-navigator-0.1.5.zip"
zip -qr "$ROOT/build/distributions/fastlane-navigator-0.1.5.zip" fastlane-navigator
"$JDK/bin/java" -cp "$KOTLIN/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -no-reflect -jvm-target 21 -jdk-home "$JDK" -classpath "$CP:$ROOT/build/local/classes" \
  -d "$ROOT/build/local/test-classes" "$ROOT/src/test/kotlin"
"$JDK/bin/java" -cp "$CP:$ROOT/build/local/classes:$ROOT/build/local/test-classes" dev.fastlane.ide.OutlineTestKt
"${FASTLANE_RUBY:-ruby}" "$ROOT/formatter/test_formatter.rb"
echo "Built: $ROOT/build/distributions/fastlane-navigator-0.1.5.zip"
