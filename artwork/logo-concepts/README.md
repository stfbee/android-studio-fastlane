# Fastlane Navigator logo variants

All four variants are SVG files with a 40×40 canvas and transparent padding:

- `01-green-diamond.svg`: green diamond with a white symbol.
- `02-yellow-diamond.svg`: yellow diamond with a dark symbol — selected plugin logo.
- `03-green-square.svg`: green square with a white symbol.
- `04-yellow-square.svg`: yellow square with a dark symbol.

The selected variant is copied to `src/main/resources/META-INF/pluginIcon.svg`. It is used for both light and dark themes. The file icon remains unchanged.

The designs combine the supplied Fastlane symbol (see [source and license](../fastlane/README.md)) with a road-sign border and navigation arrow. `preview.svg` and `preview.svg.png` compare the variants on light and dark backgrounds.

JetBrains requirements: [Plugin Logo](https://plugins.jetbrains.com/docs/intellij/plugin-icon-file.html). SVG, 40×40 canvas, at least 2px transparent padding; square signs use a 32×32 visible area. Individual logo files are under 3kB.
