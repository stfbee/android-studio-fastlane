# Changelog

English · [Русский](CHANGELOG.ru.md)

## 0.2.3

- Refreshed the plugin card and repository with a shared banner and English descriptions.
- Added documentation, issue tracker, source code, release, and developer links.
- Added Russian documentation translations and separated development instructions from the user guide.

## 0.2.2

- Added release notes to the plugin card and `CHANGELOG.md` to the repository.
- Changed the developer name to Vladislav Onishchenko.
- Added GitHub Actions for checks, ZIP builds, and GitHub Releases from version tags or manual historical releases.

## 0.2.1

- Added navigation from `with.lane` in `maierj/fastlane-action`, respecting `with.subdirectory`.
- Added same-file GitLab `extends` and `!reference` support, including inherited `before_script` commands.
- Added guards for unknown parents and inheritance cycles.
- Verified navigation in Studio against real CI files: 9 GitHub Actions targets and 6 GitLab CI targets.

## 0.2.0

- Added navigation from `.github/workflows/*.yml`, `*.yaml`, and `.gitlab-ci.yml` to lane declarations in Fastfile.
- Recognized literal `fastlane release` and `bundle exec fastlane android release` commands.
- Respected explicit platforms, `default_platform`, GitHub `working-directory`, simple `cd`, and GitLab `before_script`.
- Offered a choice with file paths when multiple Fastfiles match.
- Excluded private lanes, comments, echoed text, and dynamic names from CLI navigation.

## 0.1.6

- Fixed formatting of multiline Ruby strings, including triple-quoted literals.
- Preserved original quotes, interpolation, and internal indentation in multiline text.
- Added regression checks for string preservation and repeated formatting.

## 0.1.5

- Added formatting through **Code → Reformat Code** for supported files.
- Bundled Syntax Tree 6.3.0 and prettier_print 1.2.1, without requiring gem installation.
- Added automatic Ruby detection and a configurable executable path. The formatter requires Ruby 2.7+.
- Formatted unsaved editor text; syntax and Ruby startup errors leave documents unchanged.
- Added background execution, cancellation, and support for the IDE's Reformat Code on save.

## 0.1.4

- Added SVG plugin and file icons based on the Fastlane logo.

## 0.1.3

First version published in the repository.

- Ruby highlighting and recognition of Fastfile, Pluginfile, Gemfile, and other configuration files through TextMate.
- Structure and File Structure for platforms, public, private, and override lanes, with `desc` descriptions.
- Platform-aware navigation from calls to lane declarations within a Fastfile.
- Reverse navigation to callers and standard Find Usages support.
- Exact navigation offsets instead of the start of the file, excluding false matches in comments and strings.
- In a follow-up update of this version, changed the plugin ID to `ru.ovm.fastlane.as`. Remove the old `dev.fastlane.ide` plugin when upgrading from that ID.
