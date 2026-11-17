# Changelog

## 1.1.0 — 2026-11-17

First standalone release.

- Wikipedia lookups use the article intro (`extracts`) instead of the search snippet.
  MediaWiki resolves redirects and normalises titles, so inflected forms and common
  redirects reach the canonical article.
- Added the optional `LanguageSwitchDictionary` API and the `RU` / `EN` switch in the
  dictionary dock, dialog and floating popup (requires the host patch in `host-patch/`).
- Cyrillic words default to Russian Wikipedia and Latin words to English Wikipedia;
  an explicit pick always wins.
- A missing article in one edition falls through to the other; an unrelated search hit is
  never substituted for the word.
- The plugin remains installable on stock QTranslate 1.5.0 (API 2.1.0); the switch is
  simply not shown there.
- Wiktionary definitions and Wikimedia Commons image search are included.
