#!/usr/bin/env python3
"""Fail CI on new high-signal hardcoded user-facing Kotlin strings."""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE_ROOTS = [
    ROOT / "app" / "src" / "main" / "java",
    ROOT / "app" / "src" / "standard" / "java",
    ROOT / "app" / "src" / "freenet" / "java",
    ROOT / "wear" / "src" / "main" / "java",
]

CHECKS = [
    ("Compose Text literal", re.compile(r"\bText\s*\(\s*\"((?:\\.|[^\"])*)\"")),
    ("contentDescription literal", re.compile(r"contentDescription\s*=\s*\"((?:\\.|[^\"])*)\"")),
    ("notification title/text literal", re.compile(r"\.setContent(?:Title|Text|Info|SubText)\(\s*\"((?:\\.|[^\"])*)\"")),
    ("tile text literal", re.compile(r"\.setText\(\s*\"((?:\\.|[^\"])*)\"")),
    ("canvas text literal", re.compile(r"canvas\.drawText\(\s*\"((?:\\.|[^\"])*)\"")),
    ("message-card title literal", re.compile(r"\btitle\s*=\s*\"((?:\\.|[^\"])*)\"")),
    ("message-card body literal", re.compile(r"\bmessage\s*=\s*\"((?:\\.|[^\"])*)\"")),
    ("message-card badge literal", re.compile(r"\bbadgeText\s*=\s*\"((?:\\.|[^\"])*)\"")),
    ("message-card action literal", re.compile(r"\bprimaryActionLabel\s*=\s*\"((?:\\.|[^\"])*)\"")),
    ("toast literal", re.compile(r"Toast\.makeText\([^,]+,\s*\"((?:\\.|[^\"])*)\"")),
]

ALLOWED_LITERALS = {
    "",
    "--",
    "ZeusWatch",
}

INTERPOLATION_RE = re.compile(r"\$\{[^}]*\}|\$[A-Za-z_][A-Za-z0-9_]*")
ESCAPE_RE = re.compile(r"\\u[0-9A-Fa-f]{4}|\\.")
LETTER_RE = re.compile(r"[A-Za-z]")


def is_user_facing_literal(literal: str) -> bool:
    if literal in ALLOWED_LITERALS:
        return False
    static_text = INTERPOLATION_RE.sub("", literal)
    static_text = ESCAPE_RE.sub("", static_text)
    return bool(LETTER_RE.search(static_text))


def main() -> int:
    findings: list[str] = []
    for source_root in SOURCE_ROOTS:
        if not source_root.exists():
            continue
        for path in sorted(source_root.rglob("*.kt")):
            rel_path = path.relative_to(ROOT)
            for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
                for label, pattern in CHECKS:
                    for match in pattern.finditer(line):
                        literal = match.group(1)
                        if is_user_facing_literal(literal):
                            findings.append(f"{rel_path}:{line_number}: {label}: \"{literal}\"")

    if findings:
        print("Hardcoded user-facing strings found. Move these to string resources:")
        print("\n".join(findings))
        return 1

    print("No high-signal hardcoded user-facing Kotlin strings found.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
