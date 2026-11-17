# Notices

This repository is a standalone plugin for
[QTranslate](https://github.com/ahatem/QTranslate).

## Upstream code

Substantial portions of the plugin source are derived from QTranslate's
`plugins/wikimedia-reference` module at tag `v1.5.0`:

- <https://github.com/ahatem/QTranslate/tree/v1.5.0/plugins/wikimedia-reference>
- Copyright (c) 2023-2026 Ahmed Hatem and QTranslate contributors
- Licensed under the Mozilla Public License 2.0 (`LICENSES/MPL-2.0.txt`)

Files carrying the `SPDX-License-Identifier: MPL-2.0` header remain available under
MPL-2.0. The repository as a whole is distributed under GPL-3.0 (`LICENSE`); MPL-2.0 is
compatible with GPL-3.0, and the MPL-2.0 files keep their MPL-2.0 terms.

## Plugin API JAR

`libs/qtranslate-api-2.1.0.jar` is compiled from the QTranslate API classes and is
MPL-2.0. It is a compile-only dependency and is not included in the plugin JAR.

## Wikimedia data

Article text and images are fetched from Wikimedia projects at runtime. Article text is
available under the Creative Commons Attribution-ShareAlike licence; each Wikimedia project
and file page states the exact terms. This plugin does not redistribute article text.
