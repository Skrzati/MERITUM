# CLAUDE.md — MERITUM (Cyfrowy Asystent Ocen Okresowych, KPRM)

> **Zasada nadrzędna:** każda zmiana struktury katalogów, kontenerów, endpointów, migracji lub zasad
> wymaga aktualizacji tego pliku w tym samym commicie.

## Struktura repo
```
.
├── CLAUDE.md                 # ten plik — źródło prawdy o strukturze i zasadach
├── README.md                 # quickstart
├── docs/analiza-kprm.md      # analiza oficjalnych materiałów KPRM (wzory, use cases, BPMN, arkusze wzorcowe)
├── Makefile                  # make up / down / reset / logs / ps / build
├── infra/                    # wszystko do uruchamiania w Dockerze
│   ├── docker-compose.yml    # postgres, gotenberg, backend-core, ai-gateway, frontend (sieć: backend)
│   └── .env.example          # wzór zmiennych; infra/.env jest gitignorowany
├── backend-core/             # Java 21, Spring Boot 3.3, Maven
│   ├── Dockerfile            # multi-stage: maven build -> temurin 21 JRE alpine, user non-root
│   └── src/main/
│       ├── java/pl/gov/kprm/oceny/
│       │   ├── config/       # AppConfig (Clock Europe/Warsaw)
│       │   ├── domain/       # encje JPA + enumy (CycleStatus z mapą przejść, DgDecision, RescheduleMode/Status,
│       │   │                 #   ScopeChangeOutcome, HistorySource); EvaluationHistory, WellbeingSurvey, ScopeChange, RescheduleRequest
│       │   ├── rules/        # czysta logika prawa, bez I/O: DeadlineService, EvaluationRuleEngine, CriteriaCatalog,
│       │   │                 #   GradeRules (art. 89 ust. 2), RescheduleRules (art. 81 ust. 8), AnonymizedCount (k-anonimowość)
│       │   ├── service/      # CycleWorkflowService, EvaluationHistoryService, RescheduleService, ScopeChangeService,
│       │   │                 #   WellbeingService, HrService, SchemaAdminService, StatsService, PdfExportService,
│       │   │                 #   AiProxyService (audyt AI), AuditService, Access (autoryzacja)
│       │   ├── pdf/          # SheetHtmlRenderer (wzór arkusza A/B/C 1:1), GotenbergClient (HTML -> PDF/A-2b)
│       │   ├── repository/   # Spring Data JPA
│       │   ├── auth/         # JwtService (HS256), CurrentUserFilter (Bearer JWT / X-Demo-User-Id), SecurityHeadersFilter,
│       │   │                 #   @CurrentUser resolver
│       │   └── api/          # kontrolery REST, Dtos, Requests, CycleMapper, ApiExceptionHandler (RFC 7807)
│       └── resources/
│           ├── application.yml          # konfiguracja przez env (DB_*, GOTENBERG_URL, AI_GATEWAY_URL, JWT_SECRET,
│           │                            #   DEMO_HEADER_ENABLED, STATS_K_ANONYMITY)
│           └── db/migration/            # Flyway: V1 schemat, V2 seed, V3 workflow + absences, V4 kryteria v1 (§ 4),
│                                        #   V5 pola wzoru KPRM + historia/dobrostan/zakres/terminy + opisy kryteriów + seed demo
│   └── src/test/                        # JUnit 5: rules/, pdf/, auth/, service/ (jednostkowe) + it/ApiIntegrationTest
│                                        #   (Testcontainers PostgreSQL: migracje V1–V5, walidacja JPA, scenariusze API);
│                                        #   resources/docker-java.properties (api.version=1.44 dla Docker Engine 29+)
├── ai-gateway/               # Python 3.11 / FastAPI — AI & Privacy Gateway (kod zgodny z 3.9+)
│   ├── Dockerfile            # python:3.11-slim, user non-root
│   ├── app/main.py           # endpointy + modele Pydantic
│   ├── app/pii.py            # mask_pii / unmask_pii / PiiMasker (OSOBA, EMAIL, PESEL, KOMORKA)
│   ├── app/llm.py            # klient OpenAI-compatible; brak klucza -> MOCK, błąd -> MOCK_FALLBACK
│   ├── app/assistants.py     # sokratejski asystent, HR-partner, przygotowanie do rozmów (+ odpowiedzi MOCK)
│   ├── app/catalog.py        # 14 kryteriów (zał. nr 1) + katalog działań rozwojowych do IPRZ
│   ├── app/consistency.py    # deterministyczne reguły spójności arkusza (skalibrowane na arkuszach KPRM)
│   ├── app/guardrails.py     # brak propozycji punktów, brak treści medycznych, disclaimer AI Act
│   └── tests/                # pytest: test_pii, test_consistency, test_api, test_kprm_regression
│       └── fixtures/kprm_example_sheets.json  # pola 10 arkuszy wzorcowych KPRM + oczekiwane kody błędów
└── frontend/                 # Next.js 16 (App Router) + TypeScript + Tailwind 3, React 19
    ├── Dockerfile            # node:22-alpine, output standalone, user non-root
    ├── next.config.mjs       # rewrites: /api/ai/* -> ai-gateway, /api/* -> backend-core (wyliczane w czasie builda!)
    └── src/
        ├── app/              # strony: / (pulpit), /cycles/[id], /hr, /stats, /admin
        ├── components/       # Header (role switcher), UserProvider, RoleGate, ui.tsx (kit), cycle/* (zakładki)
        └── lib/              # api.ts (fetch + X-Demo-User-Id), types.ts (kontrakty DTO), labels.ts, useAsync.ts
```

## Kontenery (`make up`)
| Kontener | Obraz | Port host | Zależy od |
|---|---|---|---|
| oceny-postgres | postgres:16-alpine | 5432 | — |
| oceny-gotenberg | gotenberg/gotenberg:8 | 3001 | — |
| oceny-backend-core | build `backend-core/` | 8080 | postgres (healthy), gotenberg (healthy); woła ai-gateway przez `/api/core-ai` |
| oceny-ai-gateway | build `ai-gateway/` | 8000 | — (env `LLM_BASE_URL`, `LLM_API_KEY`, `LLM_LIVE`, `LLM_MODEL`) |
| oceny-frontend | build `frontend/` | 3000 | backend-core (healthy), ai-gateway (healthy) |

W sieci Dockera serwisy adresują się po nazwach (`postgres:5432`, `gotenberg:3000`).

## API (backend-core)
Uwierzytelnianie: `Authorization: Bearer <JWT>` z `POST /api/auth/gov-login` (symulacja Węzła Krajowego / Profilu
Zaufanego, HS256) albo — dla testów/Swaggera — `X-Demo-User-Id` (wyłączane `DEMO_HEADER_ENABLED=false`).
Publiczne: `/api/health`, `/api/auth/demo-users`, `/api/auth/gov-login`. Każda odpowiedź ma nagłówki
`X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: strict-origin-when-cross-origin`
(+ `Cache-Control: no-store` dla `/api`).
Błędy: 403 brak uprawnień, 404 brak zasobu, 409 niedozwolone przejście stanu, 422 naruszenie reguł (`violations`, opcjonalnie `result`).

| Metoda | Ścieżka | Kto | Efekt |
|---|---|---|---|
| GET | `/api/health` | wszyscy | status |
| GET | `/api/auth/demo-users`, `/api/auth/me` | wszyscy | logowanie demo |
| POST | `/api/auth/gov-login` | wszyscy | `{"userId"}` → `{accessToken, tokenType: "Bearer", expiresIn, identityProvider, user}` |
| GET | `/api/cycles`, `/api/cycles/{id}` | wg roli | KPRM_ADMIN wszystko; HR/DG urząd; strony cyklu; pola `criteriaDeadline`, `mandatoryGradeEligible`, `mandatoryGradeDeadline` |
| POST | `/api/cycles` | HR / bezpośredni przełożony | `{employeeId, sheetType?, startDate?, reevaluation?}` — **zawsze najnowsza obowiązująca wersja schematu**, termin z jej `period_months` (6 mies. przy ponownej ocenie) |
| POST | `/api/cycles/{id}/criteria` | oceniający | `{"additional":[{"code":"K07"}], "interviewDate"?}` — obowiązkowe wg arkusza + 0–3 z katalogu (status DRAFT_CRITERIA) |
| POST | `/api/cycles/{id}/confirm-criteria` | oceniany | → OBSERVATION (kryteria zablokowane) |
| GET/POST | `/api/cycles/{id}/notes` | oceniający (POST); oceniany widzi swoje + `isSharedAfterEval` po ocenie | |
| PUT | `/api/cycles/{id}/evaluate` | oceniający | `submit=false` → EVALUATION_DRAFT; `submit=true` → walidacja § 11 → AWAITING_ACK (+ `evaluationInterviewDate`) |
| POST | `/api/cycles/{id}/acknowledge` | oceniany | → COMPLETED, termin sprzeciwu +7 dni |
| POST | `/api/cycles/{id}/refusal` | oceniający / HR | fikcja zapoznania (powód, data) → COMPLETED, +7 dni na sprzeciw |
| POST | `/api/cycles/{id}/appeal` | oceniany | → APPEALED (w terminie); `appealDecisionDeadline` = +14 dni (art. 83 ust. 3) |
| POST | `/api/cycles/{id}/appeal/decision` | DG | CHANGE (nowe oceny) → COMPLETED; REDO → EVALUATION_DRAFT |
| GET/POST | `/api/cycles/{id}/reschedule` | oceniający (pkt 1–3) / oceniany (pkt 4) | art. 81 ust. 8: `{mode: EVALUATOR_CHANGE\|EMPLOYEE_ABSENCE\|EVALUATOR_ABSENCE\|EMPLOYEE_REQUEST, newDueDate, reason}`; warunek > 6 mies.; zawiadomienie § 8 w `noticeText` |
| POST | `/api/cycles/{id}/reschedule/{requestId}/decision` | oceniający | `{accept, comment}` — decyzja w sprawie wniosku ocenianego |
| GET/POST | `/api/cycles/{id}/scope-changes` | HR / oceniający | `{changeDate, newPositionTitle?, description, significant}` → `NONE` \| `CRITERIA_RESET` (§ 9 ust. 1) \| `EVALUATION_REQUIRED` (art. 81 ust. 6) |
| GET | `/api/audit-logs?limit=` | HR, DG (urząd), KPRM_ADMIN (wszystko) | ostatnie zdarzenia audytowe |
| POST | `/api/wellbeing` | każdy (o sobie) | `{workload, energy, support (1–5), wantsConversation}` — bez audytu, bez powiązania z oceną, tylko agregaty |
| POST | `/api/core-ai/{socratic-draft\|consistency-check\|hr-partner\|interview-prep}?cycleId=` | zalogowany | proxy do ai-gateway + wpis `AI_*` w `audit_logs` (tryb LLM, `maskedPrompt`, kody ostrzeżeń) — AI Act art. 26 |
| GET | `/api/hr/employees?q=&sort=&dir=` | HR | lista pracowników urzędu |
| POST | `/api/hr/absences` | HR | rejestracja nieobecności, przeliczenie `due_date` |
| POST | `/api/hr/handover` | HR | zmiana przełożonego (§ 9 ust. 3) |
| POST | `/api/hr/import-csv` (multipart `file`) | HR | `full_name,email,role,is_civil_servant[,supervisor_email]` |
| GET | `/api/hr/employees/{id}/gdpr-export` | HR | art. 15 RODO (cykle, notatki, nieobecności, historia ocen, ankiety, audyt) |
| GET/POST | `/api/admin/schemas` | KPRM_ADMIN | wersje schematu („prawo jako kod”) |
| GET | `/api/schemas/{id}` | każdy zalogowany | odczyt wersji schematu (katalog kryteriów dla UI) |
| GET | `/api/stats/summary` | DG, HR | tylko agregaty; każda liczność to `{value, suppressed}` — 1..k-1 → `{null, true}` (k z `STATS_K_ANONYMITY`, domyślnie 5); `wellbeing` (średnie tylko przy ≥ k odpowiedziach) |
| GET | `/api/cycles/{id}/pdf` | jak `GET /api/cycles/{id}` | PDF/A arkusza 1:1 wg wzoru (cz. I–III, średnia z przecinkiem, X w tabeli przyznania oceny, pouczenie art. 83 ust. 1); cz. III tylko urzędnik SC + ocena pozytywna + wniosek; audyt `PDF_EXPORTED` |

## API (ai-gateway, :8000)
| Metoda | Ścieżka | Efekt |
|---|---|---|
| GET | `/health` | status, `llm_mode` (LIVE / MOCK) |
| POST | `/api/ai/socratic-draft` | `masked_prompt`, `socratic_questions`, `draft_justification`, `ai_disclaimer` — nigdy nie proponuje punktów |
| POST | `/api/ai/consistency-check` | `warnings[{code, severity, message}]`, `auto_modified: false` |
| POST | `/api/ai/hr-partner` | `masked_prompt`, `burnout_risk_signal`, `conversation_starters`, `organizational_suggestions`, `appreciation_moment` |
| POST | `/api/ai/interview-prep` | `{stage: INITIAL\|EVALUATION}` → `goal`, `agenda[{topic, legal_basis, questions}]` (§ 5 ust. 1: 4 zagadnienia / § 10: 3), `difficult_situations`, `development_catalog` (IPRZ) |

Kody `consistency-check`: `CRITERIA_MISMATCH`, `FOREIGN_NAME`, `LEVEL_MISMATCH`, `AVERAGE_MISMATCH`, `DUPLICATE_TEXT`, `GENDER_MISMATCH`.
Frontend (Etap 6) powinien wołać AI przez `/api/core-ai/*` (audyt), a nie bezpośrednio `/api/ai/*`.
| — | `/swagger-ui.html` | | dokumentacja |

## Maszyna stanów
```
DRAFT_CRITERIA → OBSERVATION → EVALUATION_DRAFT ⟲ → AWAITING_ACK → COMPLETED → APPEALED
                 (OBSERVATION → AWAITING_ACK bezpośrednio)          APPEALED → COMPLETED (CHANGE) | EVALUATION_DRAFT (REDO)
                 OBSERVATION → DRAFT_CRITERIA (istotna zmiana zakresu obowiązków, § 9 ust. 1)
```
Przejścia zdefiniowane w `CycleStatus.TRANSITIONS`; każda zmiana stanu → wpis w `audit_logs`.
Zakończenie (zapoznanie, fikcja zapoznania, zmiana oceny przez DG) zapisuje wpis `evaluation_history` (SYSTEM); REDO go usuwa.

## Reguły prawne (`rules/`)
- **Skala** cząstkowa: 1, 3, 5, 7, 9.
- **Poziomy** (górne granice włączne, liczone dokładnie `sum <= granica*n`): ≤2 znacznie poniżej; ≤4 poniżej; ≤6 na poziomie; ≤8 powyżej; ≤9 znacznie powyżej. UI dostaje średnią z 1 miejscem po przecinku.
- **NEGATIVE**, gdy poziom poniżej oczekiwań LUB którakolwiek ocena = 1 (wyjaśnienie tekstowe w `explanation`).
- **Blokady**: NEGATIVE → uzasadnienie; IPRZ zawsze; część III tylko dla urzędnika SC, z umotywowaniem, wyłącznie przy ocenie
  pozytywnej (§ 11 ust. 5) i gdy oceniany nie ma 9. (najwyższego) stopnia.
- **Arkusze** (§ 3): A — kierownik urzędu; B — bezpośrednie kierowanie pracownikami; C — pozostałe stanowiska.
- **Kryteria** (§ 4–5, `CriteriaCatalog`): obowiązkowe z `config.mandatoryBySheet` (v1: A 6, B 5, C 5) + 0–3 dodatkowe
  (`criteriaRules.maxAdditional`) wyłącznie z katalogu `config.criteria` (v1: 14 kryteriów z zał. nr 1), bez powtórzeń
  i bez dublowania obowiązkowych. Kryteria własne tylko gdy `criteriaRules.allowCustom=true` (v1: false).
  Nowy schemat (`POST /api/admin/schemas`) musi przejść walidację `CriteriaCatalog.from`.
- **Terminy** (dokument KPRM „liczenie terminów”): miesiące — ten sam dzień miesiąca lub ostatni dzień miesiąca; dni — od dnia
  po zdarzeniu; sob./niedz./święto → następny dzień roboczy (święta stałe, Wigilia od 2025, Wielkanoc, Pn. Wielkanocny,
  Zielone Świątki, Boże Ciało). Kolejne nieobecności rozdzielone wyłącznie dniami wolnymi = jedna nieobecność ciągła
  (`mergeContinuous`). Nieobecność ciągła > 1 mies. przedłuża ocenę okresową (art. 81 ust. 7), nie przedłuża 6-mies.
  ponownej oceny (art. 81 ust. 9). Kryteria: 30 dni (7 po negatywnej, § 7). Sprzeciw: 7 dni (art. 83 ust. 1); decyzja DG:
  14 dni (art. 83 ust. 3); obligatoryjny stopień: 30 dni od zapoznania (art. 89 ust. 2).
- **Zmiana terminu** (art. 81 ust. 8): 4 przesłanki; dopuszczalna, gdy od ostatniej oceny minęło > 6 mies. (pierwsza ocena:
  okres oceny > 6 mies.); pisemne zawiadomienie (§ 8) generowane w `noticeText`.
- **Obligatoryjny stopień** (art. 89 ust. 2, `GradeRules`): urzędnik SC, po ostatnim stopniu dwie kolejne oceny pozytywne na
  poziomie „powyżej” / „znacznie powyżej oczekiwań”, stopień < 9 → `mandatoryGradeEligible`.
- Istniejące cykle zachowują `schema_version_id` (§ 13).

## Model danych
`offices` (+ `city`), `users` (+ `first_name`, `last_name`, `department`, `position_title`, `appointment_year`,
`civil_service_grade`, `last_grade_date`), `schema_versions` (`config_json` JSONB: kryteria z opisami, `mandatoryBySheet`),
`evaluation_cycles` (+ pola procesu z V3, daty rozmów, `evaluated_on`, `place`, `appeal_decision_deadline`),
`evaluation_history`, `employee_notes`, `absences`, `reschedule_requests`, `scope_changes`, `wellbeing_surveys`, `audit_logs`.
Seed demo (V5): 16 użytkowników, 15 cykli we wszystkich statusach (w tym 2 osoby uprawnione z art. 89 ust. 2, ponowna ocena,
sprzeciw, oczekujący wniosek o zmianę terminu), 7 ankiet dobrostanu. Enumy pilnowane CHECK-ami w SQL i `@Enumerated(STRING)` w JPA.
Hibernate `ddl-auto: validate` — schemat zmienia WYŁĄCZNIE Flyway.

## Zasady pracy

### Bezpieczeństwo
- Zero sekretów w repo. Hasła/klucze tylko w `infra/.env` (gitignorowany) lub zmiennych środowiskowych.
- Nowa zmienna env → dopisz do `infra/.env.example` z wartością-zaślepką.
- Przed commitem: `git status` + `git diff --cached` — sprawdź, że nie ma `target/`, `.env`, kluczy.
- Kontenery aplikacyjne działają jako użytkownik non-root.
- JWT podpisywany HMAC-SHA256 kluczem z `JWT_SECRET` (min. 32 bajty); weryfikacja w czasie stałym, kontrola `exp` i `iss`.
- Statystyki: k-anonimizacja egzekwowana w backendzie — surowe liczności 1..k-1 nie trafiają do JSON.
- Ankieta dobrostanu: dobrowolna, bez audytu i bez powiązania z cyklem; widoczna wyłącznie zbiorczo (i w eksporcie art. 15 dla osoby).

### Architektura
- `rules/` = czysta logika prawa, bez Springa w API metod i bez dostępu do bazy → testy jednostkowe bez kontekstu.
- `service/` = transakcje, autoryzacja (`Access`), audyt. Serwisy nie zależą od `api/`.
- Autoryzacja w cyklu opiera się na relacji (`evaluatorId`/`employeeId`), nie tylko na roli.
- Czas wyłącznie przez wstrzyknięty `Clock` (testowalność).

### Clean Code
- Małe klasy i metody o jednej odpowiedzialności; nazwy mówią *co*, nie *jak*.
- Warstwy: `api` → (serwis, gdy pojawi się logika) → `repository` → `domain`. Kontroler nie zawiera logiki biznesowej poza prostym wyborem zapytania.
- Encje nie wychodzą poza API — zawsze DTO (`record`).
- Wstrzykiwanie przez konstruktor (`@RequiredArgsConstructor`), pola `final`.
- Brak magicznych stałych — enumy, stałe lub konfiguracja.
- Komentarze tylko tam, gdzie kod nie tłumaczy *dlaczego*.
- Bez martwego kodu i zakomentowanych bloków.
- Błędy HTTP przez `ResponseStatusException` / `@ControllerAdvice`, nie przez zwracanie `null`.

### AI
- Do LLM trafia wyłącznie tekst po `PiiMasker`; `masked_prompt` zawsze zwracany (transparentność dla użytkownika).
- AI nie zmienia danych ani nie proponuje ocen; każda treść AI ma `ai_disclaimer` (AI Act art. 50).
- Demo nie może zależeć od sieci: brak klucza lub błąd LLM -> odpowiedzi deterministyczne.
- Kontrola spójności jest deterministyczna (reguły), nie LLM; regresja na 10 arkuszach wzorcowych KPRM (wykrywa 5 celowo
  umieszczonych błędów, 0 fałszywych alarmów) — każda zmiana reguł musi utrzymać ten wynik.
- Każde wywołanie AI z UI przez `/api/core-ai/*` → audyt (AI Act art. 26). W audycie tylko prompt po maskowaniu.
- `LLM_LIVE=true` lub `LLM_API_KEY` (np. `ollama`) → LIVE; inaczej MOCK.

### Frontend
- Przeglądarka rozmawia wyłącznie z Next.js (proxy przez rewrites) — brak CORS, adresy usług tylko w build args.
- Wszystkie wywołania przez `lib/api.ts` (automatyczny `X-Demo-User-Id`, błędy RFC 7807 -> `ApiError`).
- Typy w `lib/types.ts` muszą odpowiadać `Dtos.java` / modelom Pydantic — zmiana DTO = zmiana typów.
- Uprawnienia w UI wynikają z relacji do cyklu (evaluatorId/employeeId), tak jak w backendzie; UI tylko ukrywa akcje, decyduje backend.
- Reguły prawa nie są duplikowane w UI: katalog kryteriów z `/api/schemas/{id}`, wynik oceny z silnika reguł (średnia w UI to tylko podgląd).
- Dostępność: WCAG 2.1 AA — etykiety pól, focus-visible, role tablist/tab, `aria-*`, kontrast slate/navy na bieli.
- Statystyki: backend zwraca `{value, suppressed}` (k=5) — UI tylko wyświetla, nie liczy progu (do przepięcia w Etapie 6).

### Baza / migracje
- Migracje są niezmienne po wypchnięciu — zmiana = nowy plik `V{n}__opis.sql`.
- Seed demo tylko w migracjach z `seed` lub `demo_data` w nazwie.
- Testy integracyjne (`it/`) uruchamiają wszystkie migracje na PostgreSQL w Testcontainers — wymagany działający Docker
  (bez Dockera są pomijane).

### Git
- Conventional Commits: `feat:`, `fix:`, `chore:`, `refactor:`, `docs:`, `test:`.
- Jeden commit = jedna logiczna zmiana; CLAUDE.md aktualizowany w tym samym commicie.

## Komendy
```bash
make up        # build + start wszystkich kontenerów
make down      # stop
make reset     # stop + usunięcie danych (seed wgra się ponownie)
make logs      # logi
make build     # mvn clean verify lokalnie
make test      # testy backend-core
make test-ai   # testy ai-gateway (venv w ai-gateway/.venv)
make build-fe  # npm ci + next build (frontend)
```
