# Contributing

Bug reports and pull requests are welcome. For navigation issues, include a minimal Fastfile and CI snippet, the expected target, and your IDE name and build number.

## Development setup

- An IntelliJ-based IDE on platform 262.*, including TextMate.
- Java 25; the Studio-bundled runtime can be used.
- Ruby for formatter tests. CI uses Ruby 4.0.9.

Gradle defaults to `~/Applications/Android Studio.app/Contents`. Select another SDK with `-PidePath=/path/to/ide` or `INTELLIJ_HOME`. The legacy `studioPath` property and `ANDROID_STUDIO_HOME` variable remain supported. On macOS, use the app's `Contents` directory.

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

Set `INTELLIJ_HOME` to test another installed IDE. `FASTLANE_SMOKE_HOME` selects a separate report/config/cache directory for each IDE. If the target IDE ships a runtime without JDK tools, set `FASTLANE_JDK_HOME` to a full Java 25 JDK for packaging the test module. The target IDE still launches with its own runtime. The offline build helper also accepts `FASTLANE_JDK_HOME` and needs an IDE with a bundled Kotlin compiler.

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
git tag -a v0.2.6 -m "Fastlane Navigator 0.2.6"
git push origin v0.2.6
```

**Release plugin** builds the tagged source and publishes a GitHub Release with its ZIP and changelog only after checks pass. Re-running it updates the existing release. It uses the built-in `GITHUB_TOKEN`.

For a historical tag without workflows, run **Actions → Release plugin → Run workflow** from `main` and enter the tag. Release notes are read from the default branch's changelog.

## Marketplace resource links

The descriptor supplies the project homepage, vendor profile, and inline resource links. The dedicated links in the IDE's **Additional Info** tab are managed through the Marketplace listing's **Technical Information** settings:

| Field | URL |
| --- | --- |
| Website / source code | https://github.com/stfbee/android-studio-fastlane |
| Documentation | https://github.com/stfbee/android-studio-fastlane#readme |
| Issue tracker | https://github.com/stfbee/android-studio-fastlane/issues |
| Releases | https://github.com/stfbee/android-studio-fastlane/releases |
| License | https://github.com/stfbee/android-studio-fastlane/blob/main/LICENSE |

Marketplace publication is tracked in the [roadmap](ROADMAP.md).

## Licensing and notices

Original code and written documentation use the root MIT `LICENSE`. Third-party components and artwork retain their separate terms and review status in `THIRD_PARTY_NOTICES.md`. Preserve upstream license files when changing vendored components. Both Gradle and the offline build helper package the project license, third-party notices, Ruby library licenses, and the SVG collection's CC0 text in the ZIP.

The Fastlane artwork review is pending; the code license does not grant permission to use its marks or logo derivatives.
