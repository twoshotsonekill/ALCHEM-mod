"""Output formatting for aifuzz — terminal display, file writing."""
from __future__ import annotations
import csv
import json
import os
import sys
import time
from datetime import datetime
from pathlib import Path
from typing import TextIO

import colorama
from colorama import Fore, Style

colorama.init(autoreset=True)

from .results import FuzzResult


# ──────────────────────────────────────── color helpers ──────────────────────

BANNER = f"""{Fore.CYAN}{Style.BRIGHT}
        _  __
  __ _ (_)/ _|_   _ ____  ____
 / _` || | |_| | | |_  / |_  /
| (_| || |  _| |_| |/ /   / /
 \\__,_||_|_|  \\__,_/___| /___|

{Style.RESET_ALL}{Fore.WHITE}AI Prompt Fuzzer  |  ffuf-inspired  |  v0.1.0{Style.RESET_ALL}
"""

COL_PAYLOAD = Fore.CYAN + Style.BRIGHT
COL_HIT     = Fore.GREEN + Style.BRIGHT
COL_FILTER  = Fore.YELLOW
COL_ERROR   = Fore.RED
COL_META    = Fore.WHITE + Style.DIM
RESET       = Style.RESET_ALL


def _trunc(s: str, n: int = 60) -> str:
    return s[:n] + "…" if len(s) > n else s


def _tag_str(tags: list[str]) -> str:
    return f" [{','.join(tags)}]" if tags else ""


# ──────────────────────────────────────── terminal printer ───────────────────

class TerminalOutput:
    def __init__(
        self,
        *,
        no_color: bool = False,
        verbose: bool = False,
        show_errors: bool = True,
        response_preview: int = 80,
        silent: bool = False,
    ):
        self.no_color = no_color
        self.verbose = verbose
        self.show_errors = show_errors
        self.preview_len = response_preview
        self.silent = silent
        self._start = time.time()
        self._printed = 0

    def _c(self, code: str, text: str) -> str:
        return text if self.no_color else code + text + RESET

    def banner(self) -> None:
        if self.silent:
            return
        if self.no_color:
            print("aifuzz v0.1.0 - AI Prompt Fuzzer")
        else:
            print(BANNER)

    def config_line(self, key: str, value: str) -> None:
        if self.silent:
            return
        print(f" {self._c(COL_META, key+':'): <22} {value}")

    def separator(self) -> None:
        if not self.silent:
            print(self._c(COL_META, "─" * 72))

    def header(self) -> None:
        if self.silent:
            return
        self.separator()
        cols = f"{'ID': >5}  {'Payload': <20}  {'Len': >6}  {'Words': >5}  {'TokIn': >5}  {'TokOut': >5}  {'ms': >6}  Response"
        print(self._c(COL_META, cols))
        self.separator()

    def result(self, r: FuzzResult) -> None:
        if self.silent:
            return
        if r.error and self.show_errors:
            tag = self._c(COL_ERROR, "ERR")
            print(
                f" {r.index: >4}  {tag}  "
                f"{self._c(COL_PAYLOAD, _trunc(r.word, 20)): <20}  "
                f"{self._c(COL_ERROR, r.error[:60])}"
            )
            return

        if not r.visible:
            if self.verbose and not r.filtered:
                # show matched=False in dim
                payload_str = _trunc(r.word, 20)
                print(
                    self._c(COL_META, f" {r.index: >4}  SKIP  {payload_str: <20}  "
                    f"len={r.response_len}")
                )
            return

        payload_str = _trunc(r.word, 20)
        resp_str    = _trunc(r.response.replace("\n", " "), self.preview_len)
        tags_str    = _tag_str(r.tags)
        line = (
            f" {r.index: >4}  "
            f"{self._c(COL_HIT, payload_str): <20}  "
            f"{r.response_len: >6}  "
            f"{r.response_words: >5}  "
            f"{r.tokens_in: >5}  "
            f"{r.tokens_out: >5}  "
            f"{int(r.latency_ms): >6}  "
            f"{self._c(COL_HIT, resp_str)}"
            f"{self._c(COL_META, tags_str)}"
        )
        print(line)
        self._printed += 1

    def progress(self, done: int, total: int, hits: int) -> None:
        if self.silent:
            return
        pct  = done / total * 100 if total else 0
        bar  = "█" * int(pct // 5) + "░" * (20 - int(pct // 5))
        elapsed = time.time() - self._start
        rps  = done / elapsed if elapsed > 0 else 0
        eta_s = (total - done) / rps if rps > 0 else 0
        eta  = f"{int(eta_s)}s" if eta_s < 3600 else "∞"
        line = (
            f"\r {self._c(COL_META, bar)}  "
            f"{done}/{total} ({pct:.1f}%)  "
            f"{self._c(COL_HIT, str(hits))} hits  "
            f"{rps:.1f} req/s  ETA {eta}   "
        )
        print(line, end="", flush=True)

    def summary(self, total: int, hits: int, errors: int, elapsed: float) -> None:
        if self.silent:
            return
        print()
        self.separator()
        print(f" Total:   {total}")
        print(f" {self._c(COL_HIT, 'Hits:')}    {hits}")
        if errors:
            print(f" {self._c(COL_ERROR, 'Errors:')}  {errors}")
        print(f" Time:    {elapsed:.2f}s  ({total/elapsed:.1f} req/s)")
        self.separator()


# ──────────────────────────────────────── file writers ───────────────────────

def write_results(results: list[FuzzResult], path: str, fmt: str = "json") -> None:
    fmt = fmt.lower()
    p = Path(path)
    p.parent.mkdir(parents=True, exist_ok=True)

    if fmt == "json":
        _write_json(results, p)
    elif fmt == "jsonl":
        _write_jsonl(results, p)
    elif fmt == "csv":
        _write_csv(results, p)
    elif fmt == "md":
        _write_markdown(results, p)
    else:
        raise ValueError(f"Unknown output format: {fmt}")


def _write_json(results: list[FuzzResult], p: Path) -> None:
    data = {
        "generated": datetime.utcnow().isoformat() + "Z",
        "total": len(results),
        "hits": sum(1 for r in results if r.visible),
        "results": [r.to_dict() for r in results if r.visible],
    }
    with open(p, "w") as fh:
        json.dump(data, fh, indent=2)


def _write_jsonl(results: list[FuzzResult], p: Path) -> None:
    with open(p, "w") as fh:
        for r in results:
            if r.visible:
                fh.write(json.dumps(r.to_dict()) + "\n")


def _write_csv(results: list[FuzzResult], p: Path) -> None:
    visible = [r for r in results if r.visible]
    if not visible:
        return
    keys = ["index", "word", "response_len", "response_words",
            "tokens_in", "tokens_out", "latency_ms", "tags", "prompt", "response"]
    with open(p, "w", newline="") as fh:
        writer = csv.DictWriter(fh, fieldnames=keys, extrasaction="ignore")
        writer.writeheader()
        for r in visible:
            d = r.to_dict()
            d["tags"] = ",".join(d.get("tags", []))
            writer.writerow(d)


def _write_markdown(results: list[FuzzResult], p: Path) -> None:
    visible = [r for r in results if r.visible]
    with open(p, "w") as fh:
        fh.write(f"# aifuzz Results\n\n")
        fh.write(f"Generated: {datetime.utcnow().isoformat()}Z  \n")
        fh.write(f"Hits: {len(visible)}\n\n")
        fh.write("| # | Payload | Len | Words | ms | Tags | Response |\n")
        fh.write("|---|---------|-----|-------|-----|------|----------|\n")
        for r in visible:
            resp = r.response.replace("\n", " ")[:80]
            tags = ",".join(r.tags)
            fh.write(
                f"| {r.index} | `{r.word}` | {r.response_len} | "
                f"{r.response_words} | {int(r.latency_ms)} | {tags} | {resp} |\n"
            )
