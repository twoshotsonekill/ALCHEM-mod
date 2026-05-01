"""AI provider adapters for aifuzz.

Each provider exposes a single synchronous method:
    complete(prompt, system, model, **kwargs) -> (text, tokens_in, tokens_out)
"""
from __future__ import annotations
import json
import time
from abc import ABC, abstractmethod
from typing import Any

import requests


class ProviderError(Exception):
    pass


class BaseProvider(ABC):
    name: str = "base"

    @abstractmethod
    def complete(
        self,
        prompt: str,
        system: str | None = None,
        model: str = "",
        temperature: float = 1.0,
        max_tokens: int = 1024,
        timeout: int = 60,
    ) -> tuple[str, int, int]:
        """Returns (response_text, tokens_in, tokens_out)."""
        ...

    def _raise(self, resp: requests.Response) -> None:
        try:
            detail = resp.json()
        except Exception:
            detail = resp.text[:200]
        raise ProviderError(
            f"[{self.name}] HTTP {resp.status_code}: {detail}"
        )


# ──────────────────────────────────────── OpenAI-compatible ──────────────────

class OpenAIProvider(BaseProvider):
    name = "openai"
    DEFAULT_MODEL = "gpt-4o-mini"
    DEFAULT_BASE_URL = "https://api.openai.com/v1"

    def __init__(self, api_key: str, base_url: str | None = None):
        self.api_key = api_key
        self.base_url = (base_url or self.DEFAULT_BASE_URL).rstrip("/")

    def complete(
        self,
        prompt: str,
        system: str | None = None,
        model: str = "",
        temperature: float = 1.0,
        max_tokens: int = 1024,
        timeout: int = 60,
    ) -> tuple[str, int, int]:
        model = model or self.DEFAULT_MODEL
        messages = []
        if system:
            messages.append({"role": "system", "content": system})
        messages.append({"role": "user", "content": prompt})

        resp = requests.post(
            f"{self.base_url}/chat/completions",
            headers={
                "Authorization": f"Bearer {self.api_key}",
                "Content-Type": "application/json",
            },
            json={
                "model": model,
                "messages": messages,
                "temperature": temperature,
                "max_tokens": max_tokens,
            },
            timeout=timeout,
        )
        if not resp.ok:
            self._raise(resp)
        data = resp.json()
        text = data["choices"][0]["message"]["content"]
        usage = data.get("usage", {})
        return text, usage.get("prompt_tokens", 0), usage.get("completion_tokens", 0)


# ──────────────────────────────────────── Anthropic ──────────────────────────

class AnthropicProvider(BaseProvider):
    name = "anthropic"
    DEFAULT_MODEL = "claude-haiku-4-5-20251001"
    BASE_URL = "https://api.anthropic.com/v1"
    API_VERSION = "2023-06-01"

    def __init__(self, api_key: str):
        self.api_key = api_key

    def complete(
        self,
        prompt: str,
        system: str | None = None,
        model: str = "",
        temperature: float = 1.0,
        max_tokens: int = 1024,
        timeout: int = 60,
    ) -> tuple[str, int, int]:
        model = model or self.DEFAULT_MODEL
        body: dict[str, Any] = {
            "model": model,
            "max_tokens": max_tokens,
            "messages": [{"role": "user", "content": prompt}],
        }
        if system:
            body["system"] = system
        if temperature != 1.0:
            body["temperature"] = temperature

        resp = requests.post(
            f"{self.BASE_URL}/messages",
            headers={
                "x-api-key": self.api_key,
                "anthropic-version": self.API_VERSION,
                "Content-Type": "application/json",
            },
            json=body,
            timeout=timeout,
        )
        if not resp.ok:
            self._raise(resp)
        data = resp.json()
        text = data["content"][0]["text"]
        usage = data.get("usage", {})
        return text, usage.get("input_tokens", 0), usage.get("output_tokens", 0)


# ──────────────────────────────────────── Ollama ─────────────────────────────

class OllamaProvider(BaseProvider):
    name = "ollama"
    DEFAULT_MODEL = "llama3"
    DEFAULT_BASE_URL = "http://localhost:11434"

    def __init__(self, base_url: str | None = None):
        self.base_url = (base_url or self.DEFAULT_BASE_URL).rstrip("/")

    def complete(
        self,
        prompt: str,
        system: str | None = None,
        model: str = "",
        temperature: float = 1.0,
        max_tokens: int = 1024,
        timeout: int = 120,
    ) -> tuple[str, int, int]:
        model = model or self.DEFAULT_MODEL
        body: dict[str, Any] = {
            "model": model,
            "prompt": prompt,
            "stream": False,
            "options": {"temperature": temperature, "num_predict": max_tokens},
        }
        if system:
            body["system"] = system

        resp = requests.post(
            f"{self.base_url}/api/generate",
            json=body,
            timeout=timeout,
        )
        if not resp.ok:
            self._raise(resp)
        data = resp.json()
        text = data.get("response", "")
        tokens_in = data.get("prompt_eval_count", 0)
        tokens_out = data.get("eval_count", 0)
        return text, tokens_in, tokens_out


# ──────────────────────────────────────── Generic OpenAI-compat ──────────────

class GenericProvider(OpenAIProvider):
    """Any OpenAI-compatible endpoint (Together, Groq, Mistral, etc.)"""
    name = "generic"
    DEFAULT_MODEL = "gpt-3.5-turbo"


# ──────────────────────────────────────── factory ────────────────────────────

def get_provider(
    provider: str,
    api_key: str | None,
    base_url: str | None,
) -> BaseProvider:
    p = provider.lower()
    if p in ("openai", "azure"):
        if not api_key:
            raise ValueError("OpenAI provider requires --api-key or OPENAI_API_KEY")
        return OpenAIProvider(api_key, base_url)
    elif p == "anthropic":
        if not api_key:
            raise ValueError("Anthropic provider requires --api-key or ANTHROPIC_API_KEY")
        return AnthropicProvider(api_key)
    elif p == "ollama":
        return OllamaProvider(base_url)
    else:
        # Treat as generic OpenAI-compatible
        if not api_key:
            raise ValueError(f"Provider '{provider}' requires --api-key")
        return GenericProvider(api_key, base_url)
