"""Fail the quality gate when pipeline line or branch coverage regresses."""

from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path


MINIMUM_RATE = 0.50


def main() -> int:
    report_path = Path(sys.argv[1] if len(sys.argv) > 1 else "coverage.xml")
    root = ET.parse(report_path).getroot()
    failures = []

    for attribute in ("line-rate", "branch-rate"):
        rate = float(root.attrib[attribute])
        if rate < MINIMUM_RATE:
            failures.append(f"{attribute} {rate:.2%} is below {MINIMUM_RATE:.0%}")

    if failures:
        print("Pipeline coverage gate failed: " + "; ".join(failures), file=sys.stderr)
        return 1

    print("Pipeline coverage gate passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
