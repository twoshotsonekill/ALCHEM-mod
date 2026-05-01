"""Core fuzzing engine for aifuzz.

Runs wordlist payloads against an AI provider with configurable
concurrency, rate-limiting, retry logic, and result collection.
"""
from __future__ import annotations
import os
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass, field
from typing import Any, Callable

from .filters import FilterEngine
from .output import TerminalOutput
from .providers import BaseProvider, ProviderError
from .results import FuzzResult
from .wordlist import WordlistManager, render_prompt


@dataclass
class FuzzConfig:
    # Prompt
    prompt_template: str
    system_template: str | None = None

    # Provider
    provider: BaseProvider = field(default=None)       # type: ignore[assignment]
    model: str = ""
    temperature: float = 1.0
    max_tokens: int = 1024
    timeout: int = 60

    # Concurrency / rate
    threads: int = 1
    rate: float = 0.0          # max requests per second (0 = unlimited)
    delay: float = 0.0         # fixed delay between requests (seconds)

    # Retry
    retries: int = 0
    retry_delay: float = 2.0

    # Behavior
    dry_run: bool = False
    stop_on_first: bool = False
    max_results: int = 0       # 0 = unlimited

    # Session
    session_file: str | None = None  # path to save/resume progress

    # Callbacks
    on_result: Callable[[FuzzResult], None] | None = None


class RateLimiter:
    """Token-bucket rate limiter."""

    def __init__(self, rate: float):
        self.rate = rate  # requests per second
        self._lock = threading.Lock()
        self._last = time.monotonic()

    def acquire(self) -> None:
        if self.rate <= 0:
            return
        with self._lock:
            now = time.monotonic()
            gap = 1.0 / self.rate
            wait = self._last + gap - now
            if wait > 0:
                time.sleep(wait)
            self._last = time.monotonic()


class FuzzSession:
    """Optional session state for resume support."""

    def __init__(self, path: str):
        self.path = path
        self._done: set[int] = set()
        self._lock = threading.Lock()
        self._load()

    def _load(self) -> None:
        if os.path.exists(self.path):
            with open(self.path) as fh:
                for line in fh:
                    line = line.strip()
                    if line.isdigit():
                        self._done.add(int(line))

    def mark(self, index: int) -> None:
        with self._lock:
            self._done.add(index)
            with open(self.path, "a") as fh:
                fh.write(f"{index}\n")

    def is_done(self, index: int) -> bool:
        return index in self._done


class Fuzzer:
    """Orchestrates the fuzzing run."""

    def __init__(
        self,
        config: FuzzConfig,
        wordlist_manager: WordlistManager,
        filter_engine: FilterEngine,
        output: TerminalOutput,
    ):
        self.cfg = config
        self.wm = wordlist_manager
        self.engine = filter_engine
        self.out = output
        self._results: list[FuzzResult] = []
        self._hits = 0
        self._errors = 0
        self._done = 0
        self._lock = threading.Lock()
        self._stop = threading.Event()
        self._rate_limiter = RateLimiter(config.rate)
        self._session = FuzzSession(config.session_file) if config.session_file else None
        self._start_time = 0.0

    # ─────────────────────────────────────── public ───────────────────────────

    def run(self) -> list[FuzzResult]:
        total = self.wm.total
        self._start_time = time.time()

        self.out.header()

        payloads = list(self.wm.payloads())

        with ThreadPoolExecutor(max_workers=self.cfg.threads) as pool:
            futures = {
                pool.submit(self._fuzz_one, i + 1, payload, total): i
                for i, payload in enumerate(payloads)
            }
            for fut in as_completed(futures):
                if self._stop.is_set():
                    break
                result = fut.result()
                if result is not None:
                    with self._lock:
                        self._results.append(result)
                        if result.visible:
                            self._hits += 1
                        if result.error:
                            self._errors += 1

        elapsed = time.time() - self._start_time
        self.out.summary(self._done, self._hits, self._errors, elapsed)
        return self._results

    # ─────────────────────────────────────── private ─────────────────────────

    def _fuzz_one(
        self,
        index: int,
        payload: dict[str, str],
        total: int,
    ) -> FuzzResult | None:
        if self._stop.is_set():
            return None

        if self._session and self._session.is_done(index):
            return None  # skip already-done

        # Build result skeleton
        prompt = render_prompt(self.cfg.prompt_template, payload)
        system = (
            render_prompt(self.cfg.system_template, payload)
            if self.cfg.system_template
            else None
        )

        result = FuzzResult(
            index=index,
            payload=payload,
            prompt=prompt,
            model=self.cfg.model,
            provider=self.cfg.provider.name if self.cfg.provider else "dry-run",
        )

        if self.cfg.dry_run:
            result.response = f"[DRY-RUN] prompt={prompt[:60]}"
            result.matched = True
        else:
            self._rate_limiter.acquire()
            if self.cfg.delay > 0:
                time.sleep(self.cfg.delay)

            result = self._call_with_retry(result, prompt, system)

        # Filter
        self.engine.evaluate(result)

        # Callback
        if self.cfg.on_result:
            self.cfg.on_result(result)

        # Progress / output
        with self._lock:
            self._done += 1
            self.out.result(result)
            self.out.progress(self._done, total, self._hits)

        if self._session:
            self._session.mark(index)

        # Stop-on-first
        if result.visible and self.cfg.stop_on_first:
            self._stop.set()

        # Max results
        if self.cfg.max_results and self._hits >= self.cfg.max_results:
            self._stop.set()

        return result

    def _call_with_retry(
        self,
        result: FuzzResult,
        prompt: str,
        system: str | None,
    ) -> FuzzResult:
        last_err: str = ""
        for attempt in range(self.cfg.retries + 1):
            if attempt > 0:
                time.sleep(self.cfg.retry_delay * attempt)
            try:
                t0 = time.monotonic()
                text, tok_in, tok_out = self.cfg.provider.complete(
                    prompt=prompt,
                    system=system,
                    model=self.cfg.model,
                    temperature=self.cfg.temperature,
                    max_tokens=self.cfg.max_tokens,
                    timeout=self.cfg.timeout,
                )
                result.latency_ms = (time.monotonic() - t0) * 1000
                result.response = text
                result.tokens_in = tok_in
                result.tokens_out = tok_out
                return result
            except Exception as exc:
                last_err = str(exc)

        result.error = last_err[:120]
        return result
