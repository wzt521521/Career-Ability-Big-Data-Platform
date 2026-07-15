"""Small deterministic secret scanner for release CI."""
from __future__ import annotations

import argparse
import math
import re
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


ROOT = Path(__file__).resolve().parents[1]
SKIP_PARTS = {
    ".git",
    ".codegraph",
    "node_modules",
    "target",
    "dist",
    "coverage",
    ".venv",
    "__pycache__",
}


@dataclass(frozen=True)
class Finding:
    source: str
    pattern: str
    line: int
    preview: str


STATIC_PATTERNS = [
    ("private-key", re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----")),
    ("github-token", re.compile(r"\b(?:ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9_]{30,}\b")),
    ("github-fine-grained-token", re.compile(r"\bgithub_pat_[A-Za-z0-9_]{30,}\b")),
    ("openai-token", re.compile(r"\bsk-[A-Za-z0-9]{32,}\b")),
    ("aws-access-key", re.compile(r"\bAKIA[0-9A-Z]{16}\b")),
]

ASSIGNMENT_PATTERN = re.compile(
    r"(?i)\b(password|passwd|pwd|secret|token|api[_-]?key|access[_-]?key|private[_-]?key)\b"
    r"\s*[:=]\s*['\"]([^'\"]{24,})['\"]"
)

PLACEHOLDER_MARKERS = (
    "replace",
    "example",
    "placeholder",
    "local-development",
    "not-for-production",
    "ci-only",
    "ci-local",
    "dummy",
    "test",
    "{{",
    "${",
    "<",
)

HASH_PREFIXES = ("$2a$", "$2b$", "$2y$", "$argon2", "sha256:", "sha512:")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--include-history", action="store_true")
    return parser.parse_args()


def entropy(value: str) -> float:
    if not value:
        return 0.0
    counts = {char: value.count(char) for char in set(value)}
    length = len(value)
    return -sum((count / length) * math.log2(count / length) for count in counts.values())


def is_placeholder(value: str) -> bool:
    lowered = value.lower()
    return any(marker in lowered for marker in PLACEHOLDER_MARKERS) or lowered.startswith(HASH_PREFIXES)


def scan_text(source: str, text: str) -> Iterable[Finding]:
    for line_no, line in enumerate(text.splitlines(), start=1):
        for name, pattern in STATIC_PATTERNS:
            if pattern.search(line):
                yield Finding(source, name, line_no, line.strip()[:180])
        for match in ASSIGNMENT_PATTERN.finditer(line):
            value = match.group(2)
            if is_placeholder(value):
                continue
            if entropy(value) >= 3.5:
                yield Finding(source, "high-entropy-secret-assignment", line_no, line.strip()[:180])


def tracked_files() -> list[Path]:
    completed = subprocess.run(
        ["git", "ls-files", "-z"],
        cwd=ROOT.parent,
        check=True,
        capture_output=True,
    )
    paths: list[Path] = []
    for raw in completed.stdout.decode("utf-8", errors="replace").split("\0"):
        if not raw:
            continue
        path = ROOT.parent / raw
        if any(part in SKIP_PARTS for part in path.parts):
            continue
        if path.is_file():
            paths.append(path)
    return paths


def scan_current_tree() -> list[Finding]:
    findings: list[Finding] = []
    for path in tracked_files():
        try:
            content = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        findings.extend(scan_text(str(path.relative_to(ROOT.parent)), content))
    return findings


def scan_history() -> list[Finding]:
    completed = subprocess.run(
        ["git", "log", "-p", "--all", "--", ":!*.png", ":!*.jpg", ":!*.jpeg", ":!*.pdf", ":!*.jar"],
        cwd=ROOT.parent,
        check=True,
        capture_output=True,
    )
    content = completed.stdout.decode("utf-8", errors="replace")
    return list(scan_text("git-history-patches", content))


def main() -> int:
    args = parse_args()
    findings = scan_current_tree()
    if args.include_history:
        findings.extend(scan_history())
    if findings:
        print("Potential secrets found:")
        for finding in findings[:50]:
            print(f"{finding.source}:{finding.line}: {finding.pattern}: {finding.preview}")
        if len(findings) > 50:
            print(f"... {len(findings) - 50} more findings")
        return 1
    print("No high-confidence secrets found")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
