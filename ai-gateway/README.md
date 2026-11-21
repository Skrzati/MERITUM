# ai-gateway

FastAPI — AI & Privacy Gateway. LLM (OpenAI-compatible: Bielik / PLLuM) widzi wyłącznie tekst po maskowaniu PII.
Brak `LLM_API_KEY` → tryb `MOCK` (deterministyczne odpowiedzi PL); błąd LLM → `MOCK_FALLBACK`.

| Endpoint | Opis |
|---|---|
| `GET /health` | status + `llm_mode` |
| `POST /api/ai/socratic-draft` | pytania sokratejskie + szkic uzasadnienia (nigdy nie proponuje punktów) |
| `POST /api/ai/consistency-check` | reguły: CRITERIA_MISMATCH, FOREIGN_NAME, LEVEL_MISMATCH, DUPLICATE_TEXT, GENDER_MISMATCH |
| `POST /api/ai/hr-partner` | sygnał przeciążenia, otwarcia rozmowy, działania organizacyjne (bez treści medycznych) |

Docs: http://localhost:8000/docs · Testy: `make test-ai`
