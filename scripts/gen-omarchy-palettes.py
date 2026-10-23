#!/usr/bin/env python3
"""Generate the Omarchy palettes for the Android theme picker.

Every theme Omarchy ships is a directory with a colors.toml. That file names
grounds, inks and a handful of hues, but a phone UI also needs panels,
hairlines, a selection wash and a bevel, so those are derived by blending the
ground toward the ink at fixed ratios. Two values are computed from contrast
rather than from the mode: the text that sits on the accent, and the text that
sits on the destructive red.

Usage:
    scripts/gen-omarchy-palettes.py            # rewrite the Kotlin file
    scripts/gen-omarchy-palettes.py --check    # fail if it is stale

The generated file is committed; regenerate it when Omarchy adds or changes a
theme rather than editing the Kotlin by hand.
"""

from __future__ import annotations

import argparse
import sys
import tomllib
from pathlib import Path

THEMES = Path("/usr/share/omarchy/themes")
OUT = (
    Path(__file__).resolve().parent.parent
    / "android/app/src/main/kotlin/com/moviebox/app/ui/theme/OmarchyPalettes.kt"
)

ROLES = (
    "ground",
    "groundScope",
    "panel",
    "panelRaised",
    "hairline",
    "hairlineRegion",
    "frameBorder",
    "ink",
    "inkBright",
    "inkSecondary",
    "inkFaint",
    "accent",
    "onAccent",
    "accentWash",
    "accentBevel",
    "amber",
    "destructive",
    "onDestructive",
)


def parse_hex(value: str) -> tuple[int, int, int]:
    raw = value.strip().lstrip("#")
    if len(raw) == 8:  # #AARRGGBB: the palettes here are opaque, so drop alpha
        raw = raw[2:]
    if len(raw) != 6:
        raise ValueError(f"bad colour {value!r}")
    return int(raw[0:2], 16), int(raw[2:4], 16), int(raw[4:6], 16)


def to_hex(rgb: tuple[int, int, int]) -> str:
    return "#{:02X}{:02X}{:02X}".format(*rgb)


def blend(a: tuple[int, int, int], b: tuple[int, int, int], t: float) -> tuple[int, int, int]:
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))


def luminance(rgb: tuple[int, int, int]) -> float:
    def channel(c: int) -> float:
        c = c / 255
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4

    r, g, b = (channel(c) for c in rgb)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def contrast(a: tuple[int, int, int], b: tuple[int, int, int]) -> float:
    la, lb = luminance(a), luminance(b)
    lighter, darker = max(la, lb), min(la, lb)
    return (lighter + 0.05) / (darker + 0.05)


def best_text_on(background: tuple[int, int, int]) -> tuple[int, int, int]:
    """The readable text colour for a filled accent or error surface.

    The palette's own inks are tried first, but pure black and white are in the
    running too: a light theme's bright ink is dark, and a mid grey accent needs
    whichever of the two actually contrasts.
    """
    candidates = [
        (255, 255, 255),
        (0, 0, 0),
    ]
    return max(candidates, key=lambda candidate: contrast(background, candidate))


def pick(theme: dict, *keys: str, default: str | None = None) -> str:
    for key in keys:
        value = theme.get(key)
        if isinstance(value, str) and value.strip():
            return value
    if default is not None:
        return default
    raise KeyError(f"none of {keys} present")


def palette_for(name: str, theme: dict) -> dict[str, object]:
    mode = theme.get("mode", "dark")
    ground = parse_hex(pick(theme, "background"))
    ink = parse_hex(pick(theme, "foreground"))
    bright_ink = parse_hex(pick(theme, "bright_foreground", "foreground"))
    accent = parse_hex(pick(theme, "accent"))
    destructive = parse_hex(pick(theme, "red", "accent"))
    # A pale accent needs dark text on it and a dark one needs light, and the
    # theme's mode is the wrong thing to ask. Pick whichever of black or white
    # actually contrasts.
    on_accent = best_text_on(accent)
    on_destructive = best_text_on(destructive)

    roles = {
        "ground": ground,
        "groundScope": parse_hex(
            pick(theme, "darker_background", "dark_background", default=pick(theme, "background"))
        ),
        "panel": parse_hex(
            pick(theme, "lighter_background", "selection", default=pick(theme, "background"))
        ),
        "panelRaised": parse_hex(
            pick(theme, "selection", "lighter_background", default=pick(theme, "background"))
        ),
        "hairline": blend(ground, ink, 0.10),
        "hairlineRegion": blend(ground, ink, 0.18),
        "frameBorder": blend(ground, ink, 0.32),
        "ink": ink,
        "inkBright": bright_ink,
        "inkSecondary": parse_hex(
            pick(theme, "light_foreground", default=pick(theme, "foreground"))
        ),
        "inkFaint": parse_hex(
            pick(theme, "dark_foreground", default=pick(theme, "foreground"))
        ),
        "accent": accent,
        "onAccent": on_accent,
        "accentWash": blend(ground, accent, 0.18),
        "accentBevel": blend(ground, accent, 0.55),
        "amber": parse_hex(pick(theme, "orange", "yellow", default=pick(theme, "accent"))),
        "destructive": destructive,
        "onDestructive": on_destructive,
    }
    return {
        "name": name,
        "dark": mode != "light",
        "roles": {role: to_hex(roles[role]) for role in ROLES},
    }


def load() -> list[dict[str, object]]:
    if not THEMES.is_dir():
        raise SystemExit(f"{THEMES} not found; this generator reads the themes Omarchy ships")
    palettes = []
    for directory in sorted(THEMES.iterdir()):
        colors = directory / "colors.toml"
        if not colors.is_file():
            continue
        with colors.open("rb") as handle:
            theme = tomllib.load(handle)
        try:
            palettes.append(palette_for(directory.name, theme))
        except (KeyError, ValueError) as error:
            print(f"skipping {directory.name}: {error}", file=sys.stderr)
    if not palettes:
        raise SystemExit("no themes found")
    return palettes


def render(palettes: list[dict[str, object]]) -> str:
    lines = [
        "package com.moviebox.app.ui.theme",
        "",
        "import androidx.compose.ui.graphics.Color",
        "",
        "/**",
        " * GENERATED by scripts/gen-omarchy-palettes.py from the colors.toml of every",
        " * theme Omarchy ships in /usr/share/omarchy/themes. Regenerate rather than",
        " * hand-edit.",
        " *",
        " * The terminal palettes name grounds, inks and hues; the panels, hairlines,",
        " * selection wash and bevel are blended from them, and the text that sits on",
        " * the accent and on the destructive red is chosen by contrast rather than by",
        " * the theme's mode.",
        " */",
        "val OmarchyPalettes: Map<String, MovieBoxPalette> = mapOf(",
    ]
    for palette in palettes:
        lines.append(f'    "{palette["name"]}" to MovieBoxPalette(')
        lines.append(f'        dark = {str(palette["dark"]).lower()},')
        for role, value in palette["roles"].items():  # type: ignore[union-attr]
            lines.append(f"        {role} = Color(0xFF{value.lstrip('#')}),")
        lines.append("    ),")
    lines.append(")")
    lines.append("")
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="fail if the committed file is stale")
    args = parser.parse_args()

    rendered = render(load())
    if args.check:
        current = OUT.read_text() if OUT.exists() else ""
        if current != rendered:
            print(f"{OUT} is stale; run scripts/gen-omarchy-palettes.py", file=sys.stderr)
            return 1
        print(f"{OUT.name} is current")
        return 0

    OUT.write_text(rendered)
    print(f"wrote {OUT} ({rendered.count(' to MovieBoxPalette(')} palettes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
