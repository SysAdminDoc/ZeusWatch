# ZeusWatch Translation Workflow

This project uses Android XML string resources as the source of truth.

## Source Files

- Phone/tablet app source language: `app/src/main/res/values/strings.xml`
- Phone/tablet Spanish locale: `app/src/main/res/values-es/strings.xml`
- Wear source language: `wear/src/main/res/values/strings.xml`
- Wear Spanish locale: `wear/src/main/res/values-es/strings.xml`

## Local Checks

Run these before opening a localization PR:

```powershell
python tools/check_localization.py
.\gradlew.bat lintStandardDebug
```

`tools/check_localization.py` catches high-signal hardcoded Kotlin UI strings.
Android lint catches missing translations and format-argument mismatches across
locale files.

## Adding A Locale

1. Copy the matching source file into a new Android locale directory, for
   example `app/src/main/res/values-fr/strings.xml`.
2. Translate every `<string>` value while preserving placeholder ordering such
   as `%1$s`, `%2$d`, and escaped percent signs such as `%%`.
3. Repeat for `wear/src/main/res/values-<locale>/strings.xml` when the locale
   should cover Wear OS.
4. Run the local checks above.

## Weblate Setup Notes

Use two monolingual Android string-resource components:

| Component | Source | File mask |
|---|---|---|
| ZeusWatch app | `app/src/main/res/values/strings.xml` | `app/src/main/res/values-*/strings.xml` |
| ZeusWatch Wear | `wear/src/main/res/values/strings.xml` | `wear/src/main/res/values-*/strings.xml` |

Keep placeholder validation enabled. Do not accept translations that remove or
renumber format placeholders unless the source string changes at the same time.

## Current Status

As of 2026-05-17, app Spanish coverage is complete: 925 default strings and
925 Spanish strings. Wear Spanish coverage is also complete for the current Wear
string surface.
