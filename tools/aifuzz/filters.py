"""Matchers and filters for aifuzz responses.

Matchers → result is SHOWN if ANY matcher hits (default: show all).
Filters  → result is HIDDEN if ANY filter hits.

Both operate on FuzzResult attributes.
"""
from __future__ import annotations
import re
from dataclasses import dataclass, field
from typing import Callable

from .results import FuzzResult


# ─────────────────────────────────────── primitive predicate ──────────────────

Predicate = Callable[[FuzzResult], bool]


def _parse_range(spec: str) -> tuple[int | None, int | None]:
    """Parse "100", "100-200", "-200", "100-" into (lo, hi)."""
    spec = spec.strip()
    if "-" in spec:
        parts = spec.split("-", 1)
        lo = int(parts[0]) if parts[0] else None
        hi = int(parts[1]) if parts[1] else None
        return lo, hi
    val = int(spec)
    return val, val


def _in_range(value: int, lo: int | None, hi: int | None) -> bool:
    if lo is not None and value < lo:
        return False
    if hi is not None and value > hi:
        return False
    return True


# ─────────────────────────────────────── built-in matchers ───────────────────

def match_string(s: str, case_sensitive: bool = False) -> Predicate:
    """Response contains literal string."""
    needle = s if case_sensitive else s.lower()
    def _check(r: FuzzResult) -> bool:
        hay = r.response if case_sensitive else r.response.lower()
        if needle in hay:
            r.tags.append(f"ms:{s[:20]}")
            return True
        return False
    return _check


def match_regex(pattern: str, flags: int = re.IGNORECASE) -> Predicate:
    """Response matches regex."""
    rx = re.compile(pattern, flags)
    def _check(r: FuzzResult) -> bool:
        if rx.search(r.response):
            r.tags.append(f"mr:{pattern[:20]}")
            return True
        return False
    return _check


def match_length(spec: str) -> Predicate:
    """Response character length in range."""
    lo, hi = _parse_range(spec)
    def _check(r: FuzzResult) -> bool:
        if _in_range(r.response_len, lo, hi):
            r.tags.append(f"ml:{r.response_len}")
            return True
        return False
    return _check


def match_words(spec: str) -> Predicate:
    """Response word count in range."""
    lo, hi = _parse_range(spec)
    def _check(r: FuzzResult) -> bool:
        if _in_range(r.response_words, lo, hi):
            r.tags.append(f"mw:{r.response_words}")
            return True
        return False
    return _check


def match_tokens(spec: str) -> Predicate:
    """Token count (out) in range."""
    lo, hi = _parse_range(spec)
    def _check(r: FuzzResult) -> bool:
        if _in_range(r.tokens_out, lo, hi):
            r.tags.append(f"mt:{r.tokens_out}")
            return True
        return False
    return _check


def match_latency(spec: str) -> Predicate:
    """Latency in ms range."""
    lo, hi = _parse_range(spec)
    def _check(r: FuzzResult) -> bool:
        lat = int(r.latency_ms)
        if _in_range(lat, lo, hi):
            r.tags.append(f"mlat:{lat}ms")
            return True
        return False
    return _check


# ─────────────────────────────────────── built-in filters ────────────────────

def filter_string(s: str, case_sensitive: bool = False) -> Predicate:
    needle = s if case_sensitive else s.lower()
    def _check(r: FuzzResult) -> bool:
        hay = r.response if case_sensitive else r.response.lower()
        return needle in hay
    return _check


def filter_regex(pattern: str, flags: int = re.IGNORECASE) -> Predicate:
    rx = re.compile(pattern, flags)
    def _check(r: FuzzResult) -> bool:
        return bool(rx.search(r.response))
    return _check


def filter_length(spec: str) -> Predicate:
    lo, hi = _parse_range(spec)
    def _check(r: FuzzResult) -> bool:
        return _in_range(r.response_len, lo, hi)
    return _check


def filter_words(spec: str) -> Predicate:
    lo, hi = _parse_range(spec)
    def _check(r: FuzzResult) -> bool:
        return _in_range(r.response_words, lo, hi)
    return _check


# ─────────────────────────────────────── engine ──────────────────────────────

@dataclass
class FilterEngine:
    matchers: list[Predicate] = field(default_factory=list)
    filters: list[Predicate] = field(default_factory=list)
    require_all_matchers: bool = False  # False = OR, True = AND

    def evaluate(self, result: FuzzResult) -> FuzzResult:
        """Apply matchers and filters, mutating result.matched / result.filtered."""
        # Matchers
        if not self.matchers:
            result.matched = True
        elif self.require_all_matchers:
            result.matched = all(m(result) for m in self.matchers)
        else:
            result.matched = any(m(result) for m in self.matchers)

        # Filters (only if matched)
        if result.matched:
            result.filtered = any(f(result) for f in self.filters)

        return result


# ─────────────────────────────────────── factory ─────────────────────────────

def build_engine(
    match_strings: list[str] | None = None,
    match_regexes: list[str] | None = None,
    match_lengths: list[str] | None = None,
    match_words_ranges: list[str] | None = None,
    match_tokens_ranges: list[str] | None = None,
    match_latencies: list[str] | None = None,
    filter_strings: list[str] | None = None,
    filter_regexes: list[str] | None = None,
    filter_lengths: list[str] | None = None,
    filter_words_ranges: list[str] | None = None,
    require_all: bool = False,
) -> FilterEngine:
    matchers: list[Predicate] = []
    filters: list[Predicate] = []

    for s in (match_strings or []):
        matchers.append(match_string(s))
    for p in (match_regexes or []):
        matchers.append(match_regex(p))
    for l in (match_lengths or []):
        matchers.append(match_length(l))
    for w in (match_words_ranges or []):
        matchers.append(match_words(w))
    for t in (match_tokens_ranges or []):
        matchers.append(match_tokens(t))
    for lt in (match_latencies or []):
        matchers.append(match_latency(lt))

    for s in (filter_strings or []):
        filters.append(filter_string(s))
    for p in (filter_regexes or []):
        filters.append(filter_regex(p))
    for l in (filter_lengths or []):
        filters.append(filter_length(l))
    for w in (filter_words_ranges or []):
        filters.append(filter_words(w))

    return FilterEngine(matchers=matchers, filters=filters, require_all_matchers=require_all)
