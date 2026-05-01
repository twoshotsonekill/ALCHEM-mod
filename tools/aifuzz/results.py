"""Result data model for aifuzz."""
from __future__ import annotations
import time
from dataclasses import dataclass, field, asdict
from typing import Any


@dataclass
class FuzzResult:
    """Represents a single fuzzing result."""

    # Input
    payload: dict[str, str]        # {"FUZZ": "word", "FUZZ2": "word2"}
    prompt: str                    # fully-rendered prompt sent to the model

    # Response
    response: str = ""             # raw model text response
    tokens_in: int = 0
    tokens_out: int = 0
    latency_ms: float = 0.0

    # Outcome
    matched: bool = False          # passed matchers
    filtered: bool = False         # blocked by filters
    error: str | None = None

    # Metadata
    timestamp: float = field(default_factory=time.time)
    index: int = 0
    model: str = ""
    provider: str = ""

    # Classifier tags added by matchers
    tags: list[str] = field(default_factory=list)

    # ------------------------------------------------------------------ helpers
    @property
    def word(self) -> str:
        """Primary FUZZ payload for display."""
        return self.payload.get("FUZZ", next(iter(self.payload.values()), ""))

    @property
    def response_len(self) -> int:
        return len(self.response)

    @property
    def response_words(self) -> int:
        return len(self.response.split())

    @property
    def visible(self) -> bool:
        return self.matched and not self.filtered and self.error is None

    def to_dict(self) -> dict[str, Any]:
        d = asdict(self)
        d["word"] = self.word
        d["response_len"] = self.response_len
        d["response_words"] = self.response_words
        return d
