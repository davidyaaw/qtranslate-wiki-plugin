# Changelog

## 1.2.0 — 2026-11-17

- Inflected Russian forms now reach their lemma when search differs only in a short ending:
  «сессии» opens «Сессия», not "not found".
- Search candidates that are not close enough to be the answer are offered as a
  "did you mean" list. On the patched host each title is clickable.
- The RU/EN highlight now follows the edition actually shown after the previous lookup, and
  is repainted immediately when it changes.
- The host patch also recovers selection capture from a dead clipboard entry, so global
  hotkeys keep working (see `host-patch/`).

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
