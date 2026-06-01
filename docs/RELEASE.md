# Release Procedure

Operational notes for cutting a ZeusWatch release. The signing keystore
must NEVER be committed to the repo. Captured TLS pins are public SPKI
hashes and are committed through `ApiCertificatePins.hostPins`.

## Per-release checklist

1. **Bump versions** in both modules. Phone and wear move together
   (mismatched versionCodes are flagged in the roadmap).
   - `app/build.gradle.kts` → `versionCode` and `versionName`
   - `wear/build.gradle.kts` → `versionCode` and `versionName`
   - README badge: `![Version](https://img.shields.io/badge/version-X.Y.Z-blue)`
2. **Update `CHANGELOG.md`** — move `[Unreleased]` content into a dated
   version section, leave the empty `[Unreleased]` heading in place.
3. **Update `COMPLETED.md`** with the milestone row.
4. **Update `fastlane/metadata/android/en-US/changelogs/{versionCode}.txt`**
   with the user-facing summary. Phone versionCode is the file name.
5. **Capture or rotate TLS pins** for keyed endpoints:
   ```bash
   bash tools/capture_api_pins.sh
   # Pastes the output into `ApiCertificatePins.hostPins` in
   # app/src/main/java/com/sysadmindoc/nimbus/data/api/ApiCertificatePins.kt
   # The two-pin invariant (leaf + intermediate per host) is unit-tested
   # in ApiCertificatePinsTest; the build fails if a host has <2 pins.
   ```
   On Windows without OpenSSL on PowerShell's PATH:
   ```powershell
   powershell -ExecutionPolicy Bypass -File tools\capture_api_pins.ps1
   ```
   Pinned hosts cover only API-key-bearing endpoints
   (OpenWeatherMap forecast + OWM AQI on `api.openweathermap.org`, and
   Pirate Weather on `api.pirateweather.net`). Keyless public APIs
   (Open-Meteo, NWS, MET Norway, ECCC, Bright Sky, MeteoAlarm, JMA)
   are intentionally unpinned to avoid breakage from routine certificate
   rotation. Re-run both scripts when possible; they should produce the
   same `sha256/...` values for the same live chain.
6. **Tag and push** the release commit. Tag format: `vX.Y.Z`.
7. **GitHub Actions `release.yml` triggers automatically** on tag push.

## CI signing-secrets contract

`release.yml` reconstructs the signing keystore from four GitHub Actions
secrets. The build is **conditional**: missing any one of these
secrets falls back to building unsigned APKs (CI still passes; the
release artifact just needs to be signed manually before distribution).

| Secret name | Contents |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | `base64 -w0 zeuswatch.jks` |
| `SIGNING_STORE_PASSWORD`  | keystore password |
| `SIGNING_KEY_ALIAS`       | `zeuswatch` |
| `SIGNING_KEY_PASSWORD`    | key password |

Verify each artifact before advertising publicly:

```bash
jarsigner -verify -verbose -certs ZeusWatch-standard-X.Y.Z.apk
jarsigner -verify -verbose -certs ZeusWatch-freenet-X.Y.Z.apk
jarsigner -verify -verbose -certs ZeusWatch-wear-X.Y.Z.apk
```

## ACRA target email

Crash reports are routed through ACRA's mail sender (consent-gated,
no automatic upload, freenet-compatible). The target address lives in
`app/src/main/java/com/sysadmindoc/nimbus/util/CrashReporting.kt` at
`REPORT_EMAIL`. Update there if the maintainer email ever changes.

## Distribution channels

- **GitHub Releases** — primary. Both `standard` and `freenet` APKs
  uploaded automatically by `release.yml`.
- **F-Droid** — `freenet` flavor is consumed by the F-Droid build
  service from the GitHub source tree. Reproducible-build certification
  is tracked as a Next-tier roadmap item (NX-14).
- **IzzyOnDroid** — same source as F-Droid but typically faster to
  index new releases.
- **Google Play Store** — not pursued.

## Rollback procedure

If a release introduces a regression that wasn't caught in CI:

1. Tag and ship a `vX.Y.Z+1` hotfix on top of the broken release rather
   than pulling the tag — pulled tags break downstream caches (F-Droid,
   IzzyOnDroid).
2. Note the rollback in the next `CHANGELOG.md` entry.
3. If the regression is security-relevant, mention it in the GitHub
   Release notes for the broken version with the recommended upgrade
   path.
