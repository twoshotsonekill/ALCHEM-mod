#!/usr/bin/env python3
"""aifuzz – AI Prompt Fuzzer CLI

Usage examples
──────────────
# Basic: fuzz a single prompt against OpenAI
  aifuzz -w roles.txt -p "Tell me about FUZZ" --provider openai

# Multi-wordlist Cartesian fuzz
  aifuzz -w roles.txt:FUZZ -w topics.txt:FUZZ2 \
         -p "Act as a FUZZ and explain FUZZ2" --provider anthropic

# Match only responses containing "yes"
  aifuzz -w injections.txt -p "FUZZ\nDo you comply?" \
         -ms yes --provider ollama

# Filter out short responses, save JSON
  aifuzz -w wordlist.txt -p "FUZZ" -fl 0-50 -o out.json --provider openai

# Dry run to preview prompts
  aifuzz -w wordlist.txt -p "Say hello to FUZZ" --dry-run
"""
from __future__ import annotations
import os
import sys
import time
from pathlib import Path

import click
from colorama import Fore, Style

from .filters import build_engine
from .fuzzer import FuzzConfig, Fuzzer
from .output import TerminalOutput, write_results
from .providers import get_provider
from .wordlist import Wordlist, WordlistManager


# ──────────────────────────────────────── helpers ────────────────────────────

def _resolve_api_key(provider: str, api_key: str | None) -> str | None:
    if api_key:
        return api_key
    env_map = {
        "openai": "OPENAI_API_KEY",
        "anthropic": "ANTHROPIC_API_KEY",
        "azure": "AZURE_OPENAI_API_KEY",
        "groq": "GROQ_API_KEY",
        "together": "TOGETHER_API_KEY",
    }
    env = env_map.get(provider.lower())
    return os.environ.get(env, "") if env else None


def _parse_wordlist_spec(spec: str) -> tuple[str, str]:
    """Parse "path:KEYWORD" or just "path" (defaults to FUZZ)."""
    if ":" in spec:
        parts = spec.rsplit(":", 1)
        return parts[0], parts[1].upper()
    return spec, "FUZZ"


def _load_wordlists(specs: tuple[str, ...]) -> WordlistManager:
    wordlists = {}
    for spec in specs:
        path, keyword = _parse_wordlist_spec(spec)
        if keyword in wordlists:
            raise click.UsageError(f"Duplicate keyword: {keyword}")
        wordlists[keyword] = Wordlist(path)
    return WordlistManager(wordlists)


# ──────────────────────────────────────── CLI ────────────────────────────────

@click.command(context_settings={"help_option_names": ["-h", "--help"]})
# ── Wordlists ──────────────────────────────────────────────────────────────
@click.option("-w", "--wordlist", "wordlists", multiple=True, required=True,
              metavar="PATH[:KEYWORD]",
              help="Wordlist file (use - for stdin). Append :KEYWORD for named slots. "
                   "Default keyword is FUZZ.")
@click.option("--zip", "zip_mode", is_flag=True, default=False,
              help="Zip wordlists instead of Cartesian product.")
# ── Prompt ─────────────────────────────────────────────────────────────────
@click.option("-p", "--prompt", "prompt_template", required=True,
              help="Prompt template with FUZZ placeholder(s). Use quotes.")
@click.option("-sp", "--system-prompt", "system_template", default=None,
              help="System prompt template (also supports FUZZ keywords).")
@click.option("--prompt-file", "prompt_file", default=None, type=click.Path(exists=True),
              help="Load prompt template from file instead of -p.")
# ── Provider ───────────────────────────────────────────────────────────────
@click.option("--provider", default="openai", show_default=True,
              type=click.Choice(["openai", "anthropic", "ollama", "azure",
                                 "groq", "together", "generic"], case_sensitive=False),
              help="AI provider.")
@click.option("-m", "--model", "model", default="",
              help="Model name. Uses provider default if omitted.")
@click.option("-k", "--api-key", "api_key", default=None, envvar="AIFUZZ_API_KEY",
              help="API key (or set OPENAI_API_KEY / ANTHROPIC_API_KEY env var).")
@click.option("-u", "--base-url", "base_url", default=None,
              help="Custom base URL (Ollama, Azure, or OpenAI-compatible endpoint).")
@click.option("--temperature", default=1.0, show_default=True, type=float,
              help="Sampling temperature.")
@click.option("--max-tokens", default=1024, show_default=True, type=int,
              help="Max tokens per response.")
@click.option("--timeout", default=60, show_default=True, type=int,
              help="Request timeout in seconds.")
# ── Matchers ───────────────────────────────────────────────────────────────
@click.option("-ms", "--match-string", "match_strings", multiple=True,
              metavar="STRING", help="Show results containing STRING (case-insensitive).")
@click.option("-mr", "--match-regex", "match_regexes", multiple=True,
              metavar="REGEX", help="Show results matching REGEX.")
@click.option("-ml", "--match-length", "match_lengths", multiple=True,
              metavar="N|N-M", help="Show results with response length in range.")
@click.option("-mw", "--match-words", "match_words_ranges", multiple=True,
              metavar="N|N-M", help="Show results with word count in range.")
@click.option("-mt", "--match-tokens", "match_tokens_ranges", multiple=True,
              metavar="N|N-M", help="Show results with output token count in range.")
@click.option("-mlat", "--match-latency", "match_latencies", multiple=True,
              metavar="N|N-M", help="Show results with latency (ms) in range.")
@click.option("--match-all", is_flag=True, default=False,
              help="Require ALL matchers to hit (AND mode). Default is OR.")
# ── Filters ────────────────────────────────────────────────────────────────
@click.option("-fs", "--filter-string", "filter_strings", multiple=True,
              metavar="STRING", help="Hide results containing STRING.")
@click.option("-fr", "--filter-regex", "filter_regexes", multiple=True,
              metavar="REGEX", help="Hide results matching REGEX.")
@click.option("-fl", "--filter-length", "filter_lengths", multiple=True,
              metavar="N|N-M", help="Hide results with response length in range.")
@click.option("-fw", "--filter-words", "filter_words_ranges", multiple=True,
              metavar="N|N-M", help="Hide results with word count in range.")
# ── Concurrency ────────────────────────────────────────────────────────────
@click.option("-t", "--threads", default=1, show_default=True, type=int,
              help="Number of concurrent threads.")
@click.option("--rate", default=0.0, show_default=True, type=float,
              help="Max requests per second (0 = unlimited).")
@click.option("-d", "--delay", default=0.0, show_default=True, type=float,
              help="Fixed delay between requests (seconds).")
# ── Retry ──────────────────────────────────────────────────────────────────
@click.option("--retries", default=0, show_default=True, type=int,
              help="Retry failed requests N times.")
@click.option("--retry-delay", default=2.0, show_default=True, type=float,
              help="Base delay between retries (seconds, exponential backoff).")
# ── Output ─────────────────────────────────────────────────────────────────
@click.option("-o", "--output", "output_file", default=None,
              help="Save results to file.")
@click.option("-of", "--output-format", "output_format", default="json",
              type=click.Choice(["json", "jsonl", "csv", "md"], case_sensitive=False),
              show_default=True, help="Output file format.")
@click.option("--no-color", is_flag=True, help="Disable color output.")
@click.option("-v", "--verbose", is_flag=True, help="Show filtered/skipped results.")
@click.option("-s", "--silent", is_flag=True,
              help="Suppress all terminal output (useful with -o).")
@click.option("--show-errors", is_flag=True, default=True, show_default=True,
              help="Show error results in output.")
@click.option("--preview-len", default=80, show_default=True, type=int,
              help="Characters of response to preview in terminal.")
# ── Behavior ───────────────────────────────────────────────────────────────
@click.option("--dry-run", is_flag=True,
              help="Build prompts without sending API requests.")
@click.option("--stop-on-first", is_flag=True,
              help="Stop after the first matching result.")
@click.option("--max-results", default=0, type=int,
              help="Stop after N matching results (0 = unlimited).")
@click.option("--resume", "session_file", default=None,
              metavar="FILE", help="Session file for save/resume support.")
def main(
    wordlists,
    zip_mode,
    prompt_template,
    system_template,
    prompt_file,
    provider,
    model,
    api_key,
    base_url,
    temperature,
    max_tokens,
    timeout,
    match_strings,
    match_regexes,
    match_lengths,
    match_words_ranges,
    match_tokens_ranges,
    match_latencies,
    match_all,
    filter_strings,
    filter_regexes,
    filter_lengths,
    filter_words_ranges,
    threads,
    rate,
    delay,
    retries,
    retry_delay,
    output_file,
    output_format,
    no_color,
    verbose,
    silent,
    show_errors,
    preview_len,
    dry_run,
    stop_on_first,
    max_results,
    session_file,
):
    """aifuzz — AI Prompt Fuzzer\n
    Fuzz AI model prompts using wordlists, with ffuf-style filtering and output.
    """
    out = TerminalOutput(
        no_color=no_color,
        verbose=verbose,
        show_errors=show_errors,
        response_preview=preview_len,
        silent=silent,
    )
    out.banner()

    # ── Load prompt from file if specified ─────────────────────────────────
    if prompt_file:
        prompt_template = Path(prompt_file).read_text()

    # Validate FUZZ placeholders
    if not any(kw in prompt_template for kw in ["FUZZ"] + [f"FUZZ{i}" for i in range(2, 10)]):
        # Check for any custom keyword
        pass  # keywords might be different; we'll validate at runtime

    # ── Load wordlists ─────────────────────────────────────────────────────
    try:
        wm = _load_wordlists(wordlists)
        wm.mode = "zip" if zip_mode else "product"
    except (FileNotFoundError, click.UsageError) as e:
        click.echo(f"{Fore.RED}Error: {e}{Style.RESET_ALL}", err=True)
        sys.exit(1)

    # ── Resolve provider ───────────────────────────────────────────────────
    resolved_key = _resolve_api_key(provider, api_key)
    if not dry_run:
        try:
            prov = get_provider(provider, resolved_key, base_url)
        except ValueError as e:
            click.echo(f"{Fore.RED}Error: {e}{Style.RESET_ALL}", err=True)
            sys.exit(1)
    else:
        prov = None  # type: ignore[assignment]

    # ── Print config summary ───────────────────────────────────────────────
    out.config_line("Provider", f"{provider}" + (f" / {model}" if model else ""))
    out.config_line("Wordlists", ", ".join(
        f"{Path(p).name}→{k}" for p, k in
        [_parse_wordlist_spec(s) for s in wordlists]
    ))
    out.config_line("Total payloads", str(wm.total))
    out.config_line("Threads", str(threads))
    if rate:
        out.config_line("Rate limit", f"{rate} req/s")
    if delay:
        out.config_line("Delay", f"{delay}s")
    if dry_run:
        out.config_line("Mode", "DRY RUN")
    prompt_display = prompt_template.replace("\n", "\\n")[:60]
    out.config_line("Prompt", prompt_display)
    if system_template:
        out.config_line("System", system_template[:60])

    # ── Build filter engine ────────────────────────────────────────────────
    engine = build_engine(
        match_strings=list(match_strings),
        match_regexes=list(match_regexes),
        match_lengths=list(match_lengths),
        match_words_ranges=list(match_words_ranges),
        match_tokens_ranges=list(match_tokens_ranges),
        match_latencies=list(match_latencies),
        filter_strings=list(filter_strings),
        filter_regexes=list(filter_regexes),
        filter_lengths=list(filter_lengths),
        filter_words_ranges=list(filter_words_ranges),
        require_all=match_all,
    )

    # ── Build fuzzer config ────────────────────────────────────────────────
    cfg = FuzzConfig(
        prompt_template=prompt_template,
        system_template=system_template,
        provider=prov,
        model=model,
        temperature=temperature,
        max_tokens=max_tokens,
        timeout=timeout,
        threads=threads,
        rate=rate,
        delay=delay,
        retries=retries,
        retry_delay=retry_delay,
        dry_run=dry_run,
        stop_on_first=stop_on_first,
        max_results=max_results,
        session_file=session_file,
    )

    # ── Run ────────────────────────────────────────────────────────────────
    fuzzer = Fuzzer(cfg, wm, engine, out)
    try:
        results = fuzzer.run()
    except KeyboardInterrupt:
        click.echo(f"\n{Fore.YELLOW}Interrupted.{Style.RESET_ALL}")
        results = fuzzer._results

    # ── Save output ────────────────────────────────────────────────────────
    if output_file:
        write_results(results, output_file, output_format)
        if not silent:
            hits = sum(1 for r in results if r.visible)
            click.echo(
                f" Saved {Fore.GREEN}{hits}{Style.RESET_ALL} results → "
                f"{Fore.CYAN}{output_file}{Style.RESET_ALL}"
            )


if __name__ == "__main__":
    main()
