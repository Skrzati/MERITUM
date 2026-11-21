"""Klient LLM zgodny z OpenAI Chat Completions (Bielik / PLLuM / dowolny serwer OpenAI-compatible).

Tryb LIVE: ustawiony LLM_API_KEY (np. „ollama” dla lokalnego Ollama/LM Studio) albo LLM_LIVE=true (endpoint bez klucza).
W przeciwnym razie tryb MOCK — deterministyczne odpowiedzi, demo działa offline. Błąd sieci lub nieparsowalna
odpowiedź kończy się fallbackiem (MOCK_FALLBACK).
"""
import json
import logging
import os
import re
from typing import Any, Dict, Optional

import httpx

log = logging.getLogger(__name__)

DEFAULT_BASE_URL = "http://localhost:11434/v1"
DEFAULT_MODEL = "SpeakLeash/bielik-11b-v2.3-instruct"
DEFAULT_TIMEOUT_S = 30.0
JSON_OBJECT_RE = re.compile(r"\{.*\}", re.DOTALL)

MODE_LIVE = "LIVE"
MODE_MOCK = "MOCK"
MODE_FALLBACK = "MOCK_FALLBACK"
_TRUTHY = {"1", "true", "yes", "on"}


class LLMClient:
    def __init__(self, base_url: Optional[str] = None, api_key: Optional[str] = None,
                 model: Optional[str] = None, timeout: float = DEFAULT_TIMEOUT_S, live: Optional[bool] = None) -> None:
        self.base_url = (base_url or os.getenv("LLM_BASE_URL") or DEFAULT_BASE_URL).rstrip("/")
        self.api_key = api_key if api_key is not None else os.getenv("LLM_API_KEY", "")
        self.model = model or os.getenv("LLM_MODEL") or DEFAULT_MODEL
        self.timeout = timeout
        self.live = live if live is not None else os.getenv("LLM_LIVE", "").strip().lower() in _TRUTHY

    @property
    def mode(self) -> str:
        return MODE_LIVE if self.api_key or self.live else MODE_MOCK

    def chat_json(self, system: str, user: str) -> Optional[Dict[str, Any]]:
        """Zwraca sparsowany obiekt JSON z odpowiedzi modelu albo None (tryb MOCK / błąd)."""
        if self.mode == MODE_MOCK:
            return None
        try:
            response = httpx.post(
                f"{self.base_url}/chat/completions",
                headers={"Authorization": f"Bearer {self.api_key}"} if self.api_key else {},
                json={
                    "model": self.model,
                    "temperature": 0.2,
                    "messages": [{"role": "system", "content": system}, {"role": "user", "content": user}],
                },
                timeout=self.timeout,
            )
            response.raise_for_status()
            content = response.json()["choices"][0]["message"]["content"]
            match = JSON_OBJECT_RE.search(content)
            return json.loads(match.group()) if match else None
        except (httpx.HTTPError, KeyError, IndexError, ValueError) as exc:
            log.warning("LLM niedostępny, używam odpowiedzi zastępczej: %s", exc)
            return None
