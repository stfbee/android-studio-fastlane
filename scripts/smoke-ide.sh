#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT="$PWD"
IDE="${ANDROID_STUDIO_HOME:-$HOME/Applications/Android Studio.app/Contents}"
JDK="$IDE/jbr/Contents/Home"
SMOKE="$ROOT/build/ide-smoke"
mkdir -p "$SMOKE/plugins/fastlane-navigator/lib" "$SMOKE/metadata/META-INF" "$SMOKE/config"
cp -R "$ROOT/build/staging/fastlane-navigator/" "$SMOKE/plugins/fastlane-navigator/"
cat > "$SMOKE/metadata/META-INF/plugin.xml" <<'XML'
<idea-plugin>
  <id>ru.ovm.fastlane.as.smoke</id>
  <name>Fastlane IDE smoke test</name>
  <version>0.2.3</version>
  <vendor>Local tests</vendor>
  <idea-version since-build="262" until-build="262.*"/>
  <depends>com.intellij.modules.platform</depends>
  <depends>ru.ovm.fastlane.as</depends>
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
fastlane.smoke.report=$SMOKE/result.txt
EOF
STUDIO_PROPERTIES="$SMOKE/ide.properties" "$IDE/MacOS/studio" fastlane-smoke
