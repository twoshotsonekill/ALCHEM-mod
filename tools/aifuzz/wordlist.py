"""Wordlist loading and multi-wordlist Cartesian iteration for aifuzz."""
from __future__ import annotations
import itertools
import os
import sys
from pathlib import Path
from typing import Iterator


class Wordlist:
    """Loads and iterates a single wordlist file or stdin (-)."""

    def __init__(self, path: str, encoding: str = "utf-8"):
        self.path = path
        self.encoding = encoding
        self._words: list[str] = []
        self._load()

    def _load(self) -> None:
        if self.path == "-":
            self._words = [line.rstrip("\n") for line in sys.stdin if line.strip()]
        else:
            p = Path(self.path)
            if not p.exists():
                raise FileNotFoundError(f"Wordlist not found: {self.path}")
            with open(p, encoding=self.encoding, errors="replace") as fh:
                self._words = [
                    line.rstrip("\n") for line in fh if line.strip() and not line.startswith("#")
                ]

    def __len__(self) -> int:
        return len(self._words)

    def __iter__(self) -> Iterator[str]:
        return iter(self._words)

    @property
    def words(self) -> list[str]:
        return self._words


class WordlistManager:
    """
    Manages multiple named wordlists (FUZZ, FUZZ2, …) and produces
    Cartesian-product payloads or zip payloads depending on mode.
    """

    def __init__(self, wordlists: dict[str, Wordlist], mode: str = "product"):
        """
        Args:
            wordlists: mapping of placeholder → Wordlist
            mode: "product" (default, Cartesian) | "zip" (parallel)
        """
        self.wordlists = wordlists
        self.mode = mode

    @property
    def total(self) -> int:
        if self.mode == "zip":
            return min(len(wl) for wl in self.wordlists.values()) if self.wordlists else 0
        # Cartesian product
        total = 1
        for wl in self.wordlists.values():
            total *= len(wl)
        return total

    def payloads(self) -> Iterator[dict[str, str]]:
        """Yield dicts like {"FUZZ": "word1", "FUZZ2": "word2"}."""
        keys = list(self.wordlists.keys())
        lists = [self.wordlists[k].words for k in keys]

        if self.mode == "zip":
            for combo in zip(*lists):
                yield dict(zip(keys, combo))
        else:
            for combo in itertools.product(*lists):
                yield dict(zip(keys, combo))


def render_prompt(template: str, payload: dict[str, str]) -> str:
    """Replace all placeholder keys in template with payload values."""
    result = template
    for key, value in payload.items():
        result = result.replace(key, value)
    return result
