# Compile-time QTranslate API

`qtranslate-api-2.1.0.jar` contains the classes of the QTranslate plugin API 2.1.0.
It is built from the API classes in QTranslate v1.5.0 and is used only to compile this plugin;
it is **not** bundled into the plugin JAR.

QTranslate is Copyright (c) 2023-2026 Ahmed Hatem and QTranslate contributors and is
licensed under the Mozilla Public License 2.0. See `../LICENSES/MPL-2.0.txt` and
`../NOTICE.md`.

The optional `LanguageSwitchDictionary` interface is not part of this stock API JAR.
The plugin carries a forward-compatible copy in its own source so it can load on a stock
QTranslate 1.5.0 host; see the note in
`src/main/kotlin/com/github/ahatem/qtranslate/api/dictionary/LanguageSwitchDictionary.kt`.
