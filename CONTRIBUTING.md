# Contributing

Bug reports and pull requests are welcome. For navigation issues, include a minimal Fastfile and CI snippet, the expected target, and your Android Studio build number.

## Development setup

- Android Studio on IntelliJ Platform 262.*, including TextMate.
- Java 25; the Studio-bundled runtime can be used.
- Ruby for formatter tests. CI uses Ruby 4.0.9.

Gradle defaults to `~/Applications/Android Studio.app/Contents`. Override it with `-PstudioPath=/path/to/studio` or `ANDROID_STUDIO_HOME`. On macOS, use the app's `Contents` directory.

```sh
./gradlew check buildPlugin verifyPluginStructure
```

If Gradle cannot find Java 25, set `JAVA_HOME` or pass the toolchain path:

```sh
./gradlew check buildPlugin '-Dorg.gradle.java.installations.paths=/path/to/studio/jbr'
```

The ZIP is written to `build/distributions/`.

## Run and test

Launch an isolated development IDE:

```sh
./gradlew runIde
```

`check` runs Kotlin tests and Ruby formatter scenarios. Select Ruby with `-PrubyExecutable=/path/to/ruby` if needed.

The macOS helpers compile against the installed Studio SDK and run an integration check in a separate IDE instance:

```sh
bash scripts/build-local.sh
bash scripts/smoke-ide.sh
```

The smoke test covers file recognition, navigation, Find Usages, structure, formatting, and CI navigation. Results are written to `build/ide-smoke/result.txt`; a successful run ends with `PASS`. Settings and caches stay under `build/ide-smoke`.

To also check CI navigation against a local checkout, set `FASTLANE_CI_PROJECT` when running the smoke script. That checkout is read without modifying its files.

## Documentation

- `README.md` is the English user guide; `README.ru.md` is its Russian translation.
- `CHANGELOG.md` contains release notes; `CHANGELOG.ru.md` is the Russian translation.
- The plugin card uses the HTML description in `src/main/resources/META-INF/plugin.xml`.
- The shared banner is `docs/images/banner.png`; the plugin card references its public GitHub URL.

Keep installation and behavior in the README, development instructions here, and version history in the changelog.

## GitHub Actions and releases

**Build plugin** runs on `main` pushes and pull requests. It checks version metadata, runs tests, builds the ZIP, and validates its structure. Artifacts are retained for 14 days. CI pins Ruby and the Studio SDK, verifies the SDK checksum, and caches dependencies.

For a release, update the version in `build.gradle.kts`, `plugin.xml`, `bundles/fastlane/package.json`, and both helper scripts. Add a section to `CHANGELOG.md`, update the plugin change notes, and push the commit before its tag:

```sh
git tag -a v0.2.4 -m "Fastlane Navigator 0.2.4"
git push origin v0.2.4
```

**Release plugin** builds the tagged source and publishes a GitHub Release with its ZIP and changelog only after checks pass. Re-running it updates the existing release. It uses the built-in `GITHUB_TOKEN`.

For a historical tag without workflows, run **Actions → Release plugin → Run workflow** from `main` and enter the tag. Release notes are read from the default branch's changelog.

## Marketplace resource links

The descriptor supplies the project homepage, vendor profile, and inline resource links. The dedicated links in the IDE's **Additional Info** tab are managed through the Marketplace listing's **Additional Information** settings:

| Field | URL |
| --- | --- |
| Website / source code | https://github.com/stfbee/android-studio-fastlane |
| Documentation | https://github.com/stfbee/android-studio-fastlane#readme |
| Issue tracker | https://github.com/stfbee/android-studio-fastlane/issues |
| Releases | https://github.com/stfbee/android-studio-fastlane/releases |

Marketplace publication is tracked in the [roadmap](ROADMAP.md).
