#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT="$PWD"
IDE="${INTELLIJ_HOME:-${ANDROID_STUDIO_HOME:-$HOME/Applications/Android Studio.app/Contents}}"
JDK="${FASTLANE_JDK_HOME:-$IDE/jbr/Contents/Home}"
SMOKE="${FASTLANE_SMOKE_HOME:-$ROOT/build/ide-smoke}"
PRODUCT_INFO="$IDE/Resources/product-info.json"
LAUNCHER=$(ruby -rjson -e 'file = ARGV.fetch(0); info = JSON.parse(File.read(file)); launch = info.fetch("launch").find { |entry| entry["os"] == "macOS" }; puts File.expand_path(launch.fetch("launcherPath"), File.dirname(file))' "$PRODUCT_INFO")
PROPERTIES_ENV=$(ruby -rjson -e 'puts JSON.parse(File.read(ARGV.fetch(0))).fetch("envVarBaseName") + "_PROPERTIES"' "$PRODUCT_INFO")
mkdir -p "$SMOKE/plugins/fastlane-navigator/lib" "$SMOKE/metadata/META-INF" "$SMOKE/config"
cp -R "$ROOT/build/staging/fastlane-navigator/" "$SMOKE/plugins/fastlane-navigator/"
cat > "$SMOKE/metadata/META-INF/plugin.xml" <<'XML'
<idea-plugin>
  <id>ru.ovm.fastlane.navigator.smoke</id>
  <name>Fastlane IDE smoke test</name>
  <version>0.2.5</version>
  <vendor>Local tests</vendor>
  <idea-version since-build="262" until-build="262.*"/>
  <depends>com.intellij.modules.platform</depends>
  <depends>ru.ovm.fastlane.navigator</depends>
  <extensions defaultExtensionNs="com.intellij">
    <appStarter id="fastlane-smoke" implementation="dev.fastlane.ide.IdeSmokeStarter"/>
  </extensions>
</idea-plugin>
XML
mkdir -p "$SMOKE/plugins/fastlane-smoke/lib"
"$JDK/bin/jar" --create --file "$SMOKE/plugins/fastlane-smoke/lib/smoke.jar" \
  -C "$ROOT/build/local/test-classes" . -C "$SMOKE/metadata" .
cat > "$SMOKE/ide.properties" <<EOF
idea.config.path=$SMOKE/config
idea.system.path=$SMOKE/system
idea.plugins.path=$SMOKE/plugins
idea.log.path=$SMOKE/log
idea.is.internal=true
idea.trust.all.projects=true
ide.experimental.ui.onboarding=false
fastlane.smoke.report=$SMOKE/result.txt
EOF
env "$PROPERTIES_ENV=$SMOKE/ide.properties" "$LAUNCHER" fastlane-smoke
