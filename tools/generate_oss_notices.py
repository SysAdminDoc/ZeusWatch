#!/usr/bin/env python3
"""Generate the in-app open-source notices from the version catalog.

Reads every library in `gradle/libs.versions.toml`, joins it against the
curated licence map in `config/oss-licenses.json`, and writes a sorted JSON
asset the app ships. Also emits the data-provider attribution the weather
sources require.

Fails when a catalog entry has no licence mapping, so a dependency cannot be
added without someone deciding what its licence is. Run with `--check` to
verify the committed asset matches what the catalog would produce now.

Exit 0 when clean, 1 when the asset is stale or a licence is missing.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
RUNTIME_DEPENDENCIES = REPO_ROOT / "build" / "reports" / "runtime-dependencies.json"
LICENSE_MAP = REPO_ROOT / "config" / "oss-licenses.json"
OUTPUT = REPO_ROOT / "app" / "src" / "main" / "assets" / "oss_notices.json"
PROVIDER_SOURCE = (
    REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "sysadmindoc" /
    "nimbus" / "data" / "repository" / "WeatherSource.kt"
)

# Attribution the provider's terms require, beyond naming the source.
PROVIDER_ATTRIBUTION = {
    "OPEN_METEO": ("Open-Meteo", "CC-BY-4.0", "https://open-meteo.com/en/license"),
    "OPEN_METEO_BOM": ("Bureau of Meteorology via Open-Meteo", "CC-BY-4.0", "https://open-meteo.com/en/license"),
    "OPEN_METEO_KMA": ("Korea Meteorological Administration via Open-Meteo", "CC-BY-4.0", "https://open-meteo.com/en/license"),
    "OPEN_METEO_UKMO": ("UK Met Office via Open-Meteo", "CC-BY-4.0", "https://open-meteo.com/en/license"),
    "OPEN_METEO_DMI": ("Danish Meteorological Institute via Open-Meteo", "CC-BY-4.0", "https://open-meteo.com/en/license"),
    "OPEN_METEO_AIFS": ("ECMWF AIFS via Open-Meteo", "CC-BY-4.0", "https://open-meteo.com/en/license"),
    "OPEN_METEO_METEO_FRANCE": ("Meteo-France via Open-Meteo", "CC-BY-4.0", "https://open-meteo.com/en/license"),
    "FMI": ("Finnish Meteorological Institute", "CC-BY-4.0", "https://en.ilmatieteenlaitos.fi/open-data"),
    "NWS": ("US National Weather Service", "Public domain", "https://www.weather.gov/disclaimer"),
    "OPEN_WEATHER_MAP": ("OpenWeatherMap", "CC-BY-SA-4.0", "https://openweathermap.org/terms"),
    "PIRATE_WEATHER": ("Pirate Weather", "Pirate Weather Terms", "https://pirateweather.net/"),
    "BRIGHT_SKY": ("Bright Sky / DWD", "CC-BY-4.0", "https://brightsky.dev/"),
    "MET_NORWAY": ("MET Norway", "CC-BY-4.0", "https://api.met.no/doc/TermsOfService"),
    "ENVIRONMENT_CANADA": ("Environment and Climate Change Canada", "Open Government Licence - Canada", "https://eccc-msc.github.io/open-data/licence/readme_en/"),
    "HKO": ("Hong Kong Observatory", "HKSAR Government Terms", "https://data.gov.hk/en/terms-and-conditions"),
    "BMKG": ("BMKG Indonesia", "BMKG Terms", "https://data.bmkg.go.id/"),
    "GEOSPHERE_AUSTRIA": ("GeoSphere Austria", "CC-BY-4.0", "https://data.hub.geosphere.at/"),
    "METEOALARM": ("MeteoAlarm / EUMETNET", "MeteoAlarm Terms", "https://www.meteoalarm.org/en/live/page/disclaimer"),
    "JMA": ("Japan Meteorological Agency", "JMA Terms", "https://www.jma.go.jp/jma/en/copyright.html"),
}

# Non-Maven assets and services the app ships or calls that still need naming.
EXTRA_NOTICES = [
    {
        "name": "Meteocons",
        "version": "bundled",
        "license": "MIT",
        "url": "https://bas.dev/work/meteocons",
        "standardOnly": False,
    },
    {
        "name": "RainViewer radar tiles",
        "version": "public API",
        "license": "RainViewer Terms",
        "url": "https://www.rainviewer.com/api.html",
        "standardOnly": False,
    },
    {
        "name": "LibreWXR radar and CAP alerts",
        "version": "public API",
        "license": "AGPL-3.0",
        "url": "https://github.com/JoshuaKimsey/LibreWXR",
        "standardOnly": False,
    },
    {
        "name": "OpenStreetMap base map",
        "version": "tile service",
        "license": "ODbL-1.0",
        "url": "https://www.openstreetmap.org/copyright",
        "standardOnly": False,
    },
    {
        "name": "Blitzortung lightning network",
        "version": "public feed",
        "license": "Blitzortung Terms",
        "url": "https://www.blitzortung.org/en/cover_your_area.php",
        "standardOnly": False,
    },
]


def read_runtime_modules() -> dict[str, set[str]]:
    """Resolved runtime modules per shipped variant, as `group:name:version`.

    Produced by the Gradle `exportRuntimeDependencies` task. The version
    catalog is not a usable source here: dependencies declared as string
    literals never appear in it, BOM-managed artifacts carry no version, and
    test-only entries do appear.
    """
    if not RUNTIME_DEPENDENCIES.is_file():
        raise SystemExit(
            f"{RUNTIME_DEPENDENCIES.relative_to(REPO_ROOT)} is missing. "
            "Run: gradlew exportRuntimeDependencies"
        )
    raw = json.loads(RUNTIME_DEPENDENCIES.read_text(encoding="utf-8"))
    return {variant: set(modules) for variant, modules in raw.items()}


def parse_providers(text: str) -> list[str]:
    """Enum constant names from WeatherSourceProvider, in declaration order."""
    body = re.search(r"enum class WeatherSourceProvider\((?:[^)]*)\)\s*\{(.*?)\n\}", text, re.S)
    if not body:
        return []
    return re.findall(r"^\s{4}([A-Z][A-Z0-9_]*)\(", body.group(1), re.MULTILINE)


def build_notices() -> tuple[dict, list[str]]:
    runtime = read_runtime_modules()
    # The phone app is what ships the notices screen, so the asset describes
    # the phone's two flavors. Wear resolves its own 173 modules and has no
    # screen to show them on; that gap is tracked in ROADMAP.md rather than
    # papered over by listing wear-only artifacts here as if the phone
    # shipped them.
    shipped = runtime["standard"] | runtime["freenet"]

    license_data = json.loads(LICENSE_MAP.read_text(encoding="utf-8"))
    groups = license_data["groups"]

    problems: list[str] = []
    seen: dict[str, dict] = {}
    for module in sorted(shipped):
        group, name, version = module.rsplit(":", 2)
        entry = groups.get(group)
        if entry is None:
            problems.append(f"No licence mapped for group '{group}' (module {module})")
            continue
        # Two flavors can resolve different versions of the same artifact
        # (guava lands on -android for one and -jre for the other); one row
        # per artifact, and it is standard-only only if freenet has no
        # version of it at all.
        key = f"{group}:{name}"
        in_freenet = any(m.rsplit(":", 1)[0] == key for m in runtime["freenet"])
        seen[key] = {
            "name": key,
            "version": version,
            "license": entry["license"],
            "url": entry["url"],
            # Derived from the classpaths themselves rather than a hand list,
            # so it cannot drift from what each flavor really ships.
            "standardOnly": not in_freenet,
        }

    dependencies = sorted(seen.values(), key=lambda item: item["name"])
    dependencies.extend(sorted(EXTRA_NOTICES, key=lambda item: item["name"]))

    provider_names = parse_providers(PROVIDER_SOURCE.read_text(encoding="utf-8"))
    providers = []
    for name in provider_names:
        attribution = PROVIDER_ATTRIBUTION.get(name)
        if attribution is None:
            problems.append(f"No attribution mapped for weather source '{name}'")
            continue
        display, license_name, url = attribution
        providers.append({"name": display, "license": license_name, "url": url})

    notices = {
        "dependencies": dependencies,
        "providers": sorted(providers, key=lambda item: item["name"]),
    }
    return notices, problems


def render(notices: dict) -> str:
    # sort_keys plus a fixed indent keeps the output byte-identical between runs.
    return json.dumps(notices, indent=2, sort_keys=True, ensure_ascii=False) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--check",
        action="store_true",
        help="fail instead of writing when the committed asset is out of date",
    )
    args = parser.parse_args()

    notices, problems = build_notices()
    if problems:
        print(f"Open-source notices generation found {len(problems)} problem(s):")
        for problem in problems:
            print(f"  - {problem}")
        print("Add the missing entry to config/oss-licenses.json.")
        return 1

    rendered = render(notices)
    existing = OUTPUT.read_text(encoding="utf-8") if OUTPUT.exists() else None
    if args.check:
        if existing != rendered:
            print(f"{OUTPUT.relative_to(REPO_ROOT)} is out of date. Run tools/generate_oss_notices.py.")
            return 1
        print(
            f"Open-source notices are current: {len(notices['dependencies'])} dependencies, "
            f"{len(notices['providers'])} data providers."
        )
        return 0

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(rendered, encoding="utf-8", newline="\n")
    print(
        f"Wrote {OUTPUT.relative_to(REPO_ROOT)}: {len(notices['dependencies'])} dependencies, "
        f"{len(notices['providers'])} data providers."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
