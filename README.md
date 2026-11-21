# MERITUM — Cyfrowy Asystent Ocen Okresowych (KPRM)

## Start (jedna komenda)
```bash
make up
```
Tworzy `infra/.env` z `infra/.env.example` (jeśli brak), buduje i uruchamia wszystkie kontenery.
Bez `make`: `cp infra/.env.example infra/.env && docker compose -f infra/docker-compose.yml up -d --build --wait`

| Serwis | URL |
|---|---|
| **Frontend (demo)** | **http://localhost:3000** |
| backend-core API | http://localhost:8080/api/health |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| PostgreSQL | 127.0.0.1:5432 (tylko lokalnie) |

ai-gateway i Gotenberg nie mają portów hosta — są dostępne wyłącznie dla backend-core w sieci Dockera
(AI tylko przez audytowane `/api/core-ai/*`).

Logowanie demo: frontend loguje automatycznie (JWT z `POST /api/auth/gov-login`); w Swaggerze/curl — token z
`gov-login` jako `Authorization: Bearer <token>` albo nagłówek `X-Demo-User-Id: <id>` (wyłączany `DEMO_HEADER_ENABLED=false`).

`make down` · `make reset` (kasuje dane) · `make logs` · `make ps`

## Najważniejsze moduły

- Pełny proces oceny (kryteria → rozmowy → notatki/check-iny → ocena → zapoznanie/fikcja → sprzeciw), PDF/A 1:1 ze wzorem,
  paczka EZD RP (`/api/cycles/{id}/ezd-zip`: PDF + metadane KRI), zaproszenia `.ics`.
- Kadry: przypisywanie cykli (pojedynczo z kreatorem A/B/C i grupowo), zastępstwa, edycja/anonimizacja, historia ocen,
  zaległości z eskalacją, raport CSV, tryb korekty.
- Wirtualny HR Partner: „Mój zespół” (`/team` — mapa kompetencji, mentoring, radar przeciążenia), bieżąca informacja
  zwrotna (FUKO), strukturalny IPRZ z zatwierdzeniem (art. 108), przygotowanie ocenianego do rozmowy.
- Prawo jako kod: wersje schematu z kryteriami, okresem, terminami (`deadlines`) i progami poziomów (`levelThresholds`)
  edytowane w `/admin` — nowelizacja bez zmiany kodu; urzędy bez DG obsługiwane (kierownik urzędu).
- Opcjonalny lokalny LLM: `docker compose -f infra/docker-compose.yml --profile local-llm up -d` (ollama, sieć wewnętrzna).

