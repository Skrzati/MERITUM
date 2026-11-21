# ai-gateway

FastAPI — AI & Privacy Gateway. LLM (OpenAI-compatible: Bielik / PLLuM) widzi wyłącznie tekst po maskowaniu PII.
Brak `LLM_API_KEY` → tryb `MOCK` (deterministyczne odpowiedzi PL); błąd LLM → `MOCK_FALLBACK`.

| Endpoint | Opis |
|---|---|
| `GET /health` | status + `llm_mode` |
| `POST /api/ai/socratic-draft` | pytania sokratejskie + szkic uzasadnienia (nigdy nie proponuje punktów) |
| `POST /api/ai/consistency-check` | reguły: CRITERIA_MISMATCH, FOREIGN_NAME, LEVEL_MISMATCH, DUPLICATE_TEXT, GENDER_MISMATCH |
| `POST /api/ai/hr-partner` | sygnał przeciążenia, otwarcia rozmowy, działania organizacyjne (bez treści medycznych) |
| `POST /api/ai/interview-prep` | plan rozmowy: `INITIAL` (§ 5 ust. 1), `EVALUATION` (§ 10), `FEEDBACK` (bieżąca informacja zwrotna, FUKO + docenienie) + katalog działań do IPRZ |
| `POST /api/ai/suggest-criteria` | max 3 kryteria dodatkowe z katalogu (spoza obowiązkowych) + oczekiwania do rozmowy rozpoczynającej |

Bezpieczeństwo: żądania > 64 KB → 413; maskowanie PII (osoby, w tym WERSALIKI i nazwiska dwuczłonowe, e-mail, PESEL,
telefon, komórki) o koszcie liniowym; guardrails usuwają propozycje punktów i treści zdrowotne. Serwis nie ma portu
hosta — UI woła AI wyłącznie przez audytowane `/api/core-ai/*` w backend-core.

Docs: http://localhost:8000/docs · Testy: `make test-ai`
