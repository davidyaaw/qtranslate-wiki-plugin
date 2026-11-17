# Host patch for the RU/EN switch

The plugin installs and improves Wikipedia on stock QTranslate 1.5.0. The **RU / EN**
button needs a small host change, because the stock dictionary window has no place to put a
language control and the stock lookup has no concept of a dictionary offering several
editions of the same word.

This directory contains that host change as a patch against QTranslate **v1.5.0**.

## What the patch adds

- `LanguageSwitchDictionary`, an optional plugin API (API 2.2.0).
- Platform script-aware edition choice: Cyrillic → Russian, Latin → English.
- Fallback to the other edition when the first has no article.
- The `RU` / `EN` control in the dictionary dock, dialog and floating popup.
- Localized tooltips.
- **Clickable "did you mean" suggestions** when a dictionary returns search candidates instead
  of an article.
- **Selection capture recovers from a dead clipboard entry.** Windows can leave an image or
  other delayed-rendered entry whose owner has released its data; every read then fails with
  `system clipboard data unavailable`. The old code aborted capture, which made every global
  hotkey do nothing. The patch clears that unreadable entry and continues; transient
  "clipboard busy" failures still abort and preserve the clipboard.

The plugin's `minApiVersion` stays at `2.1.0`. Its own forward-compatible copy of
`LanguageSwitchDictionary` lets it load on a stock host; on a patched host the parent
classloader's interface is used, so the host recognises the service and shows the switch.

## Apply and build

Requirements: JDK 21, Git, and a QTranslate source checkout at `v1.5.0`.

```bash
git clone https://github.com/ahatem/QTranslate.git
cd QTranslate
git checkout v1.5.0

git apply /path/to/qtranslate-host-ru-en.patch

./gradlew :app:shadowJar
```

The built application JAR is `app/build/libs/QTranslate.jar`.

## Install

Back up the installed files first, then close QTranslate and replace only the
application JAR:

```text
app/QTranslate.jar
```

Keep the `plugins/`, `runtime/`, `languages/` and `datastore/` directories. The patch also
adds the `dictionary_dialog.language_tooltip` string to `languages/en-GB.toml` and
`languages/ru-RU.toml`; copy those two files if your installation keeps languages outside
the JAR.

On Windows the portable layout is typically:

```text
C:\QTranslate\app\QTranslate.jar
```

## Rollback

Restore the previous `app/QTranslate.jar` from your backup. The plugin JAR is independent
and can stay installed; on an unpatched host it still provides the improved Wikipedia
lookup, just without the switch.

## Licence

The patch modifies QTranslate, which is licensed under the Mozilla Public License 2.0.
See `../LICENSES/MPL-2.0.txt` and `../NOTICE.md`.
