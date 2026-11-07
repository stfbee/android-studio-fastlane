# Third-party notices

The project's [MIT license](LICENSE) applies to its original source code and original written documentation. It does not relicense the third-party components, artwork, or trademarks listed below. Their rights and conditions remain separate.

## Bundled Ruby libraries

| Component | Version | Copyright | License |
| --- | --- | --- | --- |
| [Syntax Tree](https://github.com/ruby-syntax-tree/syntax_tree) | 6.3.0 | Copyright (c) 2021-present Kevin Newton | MIT |
| [prettier_print](https://github.com/ruby-syntax-tree/prettier_print) | 1.2.1 | Copyright (c) 2022-present Kevin Newton | MIT |

Both libraries are distributed with their original license and copyright notices, in the source repository and the plugin ZIP:

- `formatter/vendor/syntax_tree-6.3.0/LICENSE`
- `formatter/vendor/prettier_print-1.2.1/LICENSE`

The upstream library files are unmodified. The project's multiline-string formatting customization is maintained separately in `formatter/preserve_multiline_strings.rb`.

## Fastlane artwork: provenance and pending review

Fastlane-related artwork is **outside the project's MIT license grant**. The review of permission to use the Fastlane logo and its derivatives, including as the plugin's own icon, is pending. This document records provenance; it does not assert that those uses have been cleared.

Sources:

- The original favicon was obtained from [fastlane/docs](https://github.com/fastlane/docs/blob/master/docs/img/favicon.ico). The same asset appears in [fastlane/fastlane.tools](https://github.com/fastlane/fastlane.tools/blob/master/source/images/favicon.ico).
- The supplied Fastlane SVG has paths and colors matching [gilbarbara/logos](https://github.com/gilbarbara/logos/blob/main/logos/fastlane.svg). That collection publishes a [CC0 1.0 dedication](https://github.com/gilbarbara/logos/blob/main/LICENSE.txt), while its README states that logos remain the property of their respective owners.

The collection's CC0 text is preserved at `artwork/fastlane/VECTOR-LICENSE.txt` in the source repository and `licenses/fastlane/VECTOR-LICENSE.txt` in the plugin ZIP. Including that text records the collection's terms; it is not confirmation of the original logo owner's permission. CC0 does not grant trademark rights.

Affected source assets include:

- `artwork/fastlane/` and `artwork/logo-concepts/`.
- `src/main/resources/icons/fastlaneFile.svg` and `src/main/resources/META-INF/pluginIcon.svg`, bundled inside the plugin JAR.
- `docs/images/banner.png`, which incorporates the plugin icon and is referenced by the plugin description.

The Fastlane name, logo, and other third-party marks remain subject to their owners' rights. This project is an independent integration and does not grant trademark rights or claim endorsement by Fastlane or JetBrains.

## Host platform

IntelliJ Platform and TextMate APIs are provided by the host IDE. Their binaries are not redistributed in the plugin ZIP, and their own license terms apply separately. Refer to the host IDE's distribution notices.

## Distribution contents

The plugin ZIP includes this file, the project's `LICENSE`, the two bundled Ruby library licenses, and the SVG collection's CC0 text. Keep these notices with redistributed copies. The artwork review remains pending independently of the code license.
