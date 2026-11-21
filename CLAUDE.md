# CLAUDE.md — MERITUM (Cyfrowy Asystent Ocen Okresowych, KPRM)

> **Zasada nadrzędna:** każda zmiana struktury katalogów, kontenerów, endpointów, migracji lub zasad
> wymaga aktualizacji tego pliku w tym samym commicie.

## Struktura repo
```
.
├── CLAUDE.md                 # ten plik — źródło prawdy o strukturze i zasadach
├── README.md                 # quickstart
├── Makefile                  # make up / down / reset / logs / ps / build
├── infra/                    # wszystko do uruchamiania w Dockerze
│   ├── docker-compose.yml    # postgres, gotenberg, backend-core (sieć: backend)
│   └── .env.example          # wzór zmiennych; infra/.env jest gitignorowany
├── backend-core/             # Java 21, Spring Boot 3.3, Maven
│   ├── Dockerfile            # multi-stage: maven build -> temurin 21 JRE alpine, user non-root
│   └── src/main/
│       ├── java/pl/gov/kprm/oceny/
│       │   ├── config/       # AppConfig (Clock Europe/Warsaw)
│       │   ├── domain/       # encje JPA + enumy (Role, SheetType, CycleStatus z mapą przejść, DgDecision)
│       │   ├── rules/        # czysta logika prawa, bez I/O: DeadlineService, EvaluationRuleEngine, CriteriaCatalog, rekordy reguł
│       │   ├── service/      # przypadki użycia: CycleWorkflowService, HrService, SchemaAdminService,
│       │   │                 #   StatsService, AuditService, Access (autoryzacja)
│       │   ├── repository/   # Spring Data JPA
│       │   ├── auth/         # demo-login: DemoUserFilter (X-Demo-User-Id), @CurrentUser resolver
│       │   └── api/          # kontrolery REST, Dtos, Requests, ApiExceptionHandler (RFC 7807)
│       └── resources/
│           ├── application.yml          # konfiguracja wyłącznie przez env (DB_URL, DB_USER, DB_PASSWORD, GOTENBERG_URL)
│           └── db/migration/            # Flyway: V1 schemat, V2 seed demo, V3 workflow + absences, V4 korekta kryteriów v1 (§ 4)
│   └── src/test/java/.../rules/         # JUnit 5: DeadlineServiceTest, EvaluationRuleEngineTest, CriteriaCatalogTest (czyta JSON z V4)
├── ai-gateway/               # (etap 2+) serwis AI — na razie pusty
└── frontend/                 # (etap 2+) aplikacja webowa — na razie pusta
```

## Kontenery (`make up`)
| Kontener | Obraz | Port host | Zależy od |
|---|---|---|---|
| oceny-postgres | postgres:16-alpine | 5432 | — |
| oceny-gotenberg | gotenberg/gotenberg:8 | 3001 | — |
| oceny-backend-core | build `backend-core/` | 8080 | postgres (healthy), gotenberg (healthy) |

W sieci Dockera serwisy adresują się po nazwach (`postgres:5432`, `gotenberg:3000`).

## API (backend-core)
Wszystko poza `/api/health` i `/api/auth/demo-users` wymaga nagłówka `X-Demo-User-Id`.
Błędy: 403 brak uprawnień, 404 brak zasobu, 409 niedozwolone przejście stanu, 422 naruszenie reguł (`violations`, opcjonalnie `result`).

| Metoda | Ścieżka | Kto | Efekt |
|---|---|---|---|
| GET | `/api/health` | wszyscy | status |
| GET | `/api/auth/demo-users`, `/api/auth/me` | wszyscy | logowanie demo |
| GET | `/api/cycles`, `/api/cycles/{id}` | wg roli | KPRM_ADMIN wszystko; HR/DG urząd; strony cyklu |
| POST | `/api/cycles/{id}/criteria` | oceniający | body `{"additional":[{"code":"K07"}]}` — obowiązkowe wg arkusza + 0–3 z katalogu (status DRAFT_CRITERIA) |
| POST | `/api/cycles/{id}/confirm-criteria` | oceniany | → OBSERVATION (kryteria zablokowane) |
| GET/POST | `/api/cycles/{id}/notes` | oceniający (POST); oceniany widzi swoje + `isSharedAfterEval` po ocenie | |
| PUT | `/api/cycles/{id}/evaluate` | oceniający | `submit=false` → EVALUATION_DRAFT; `submit=true` → walidacja § 11 → AWAITING_ACK |
| POST | `/api/cycles/{id}/acknowledge` | oceniany | → COMPLETED, termin sprzeciwu +7 dni |
| POST | `/api/cycles/{id}/refusal` | oceniający / HR | fikcja zapoznania (powód, data) → COMPLETED, +7 dni na sprzeciw |
| POST | `/api/cycles/{id}/appeal` | oceniany | → APPEALED (w terminie) |
| POST | `/api/cycles/{id}/appeal/decision` | DG | CHANGE (nowe oceny) → COMPLETED; REDO → EVALUATION_DRAFT |
| GET | `/api/hr/employees?q=&sort=&dir=` | HR | lista pracowników urzędu |
| POST | `/api/hr/absences` | HR | rejestracja nieobecności, przeliczenie `due_date` |
| POST | `/api/hr/handover` | HR | zmiana przełożonego (§ 9 ust. 3) |
| POST | `/api/hr/import-csv` (multipart `file`) | HR | `full_name,email,role,is_civil_servant[,supervisor_email]` |
| GET | `/api/hr/employees/{id}/gdpr-export` | HR | art. 15 RODO |
| GET/POST | `/api/admin/schemas` | KPRM_ADMIN | wersje schematu („prawo jako kod”) |
| GET | `/api/stats/summary` | DG, HR | tylko agregaty |
| — | `/swagger-ui.html` | | dokumentacja |

## Maszyna stanów
```
DRAFT_CRITERIA → OBSERVATION → EVALUATION_DRAFT ⟲ → AWAITING_ACK → COMPLETED → APPEALED
                 (OBSERVATION → AWAITING_ACK bezpośrednio)          APPEALED → COMPLETED (CHANGE) | EVALUATION_DRAFT (REDO)
```
Przejścia zdefiniowane w `CycleStatus.TRANSITIONS`; każda zmiana stanu → wpis w `audit_logs`.

## Reguły prawne (`rules/`)
- **Skala** cząstkowa: 1, 3, 5, 7, 9.
- **Poziomy** (górne granice włączne, liczone dokładnie `sum <= granica*n`): ≤2 znacznie poniżej; ≤4 poniżej; ≤6 na poziomie; ≤8 powyżej; ≤9 znacznie powyżej. UI dostaje średnią z 1 miejscem po przecinku.
- **NEGATIVE**, gdy poziom poniżej oczekiwań LUB którakolwiek ocena = 1 (wyjaśnienie tekstowe w `explanation`).
- **Blokady**: NEGATIVE → uzasadnienie; IPRZ zawsze; część III tylko dla urzędnika SC i z umotywowaniem.
- **Arkusze** (§ 3): A — kierownik urzędu; B — bezpośrednie kierowanie pracownikami; C — pozostałe stanowiska.
- **Kryteria** (§ 4–5, `CriteriaCatalog`): obowiązkowe z `config.mandatoryBySheet` (v1: A 6, B 5, C 5) + 0–3 dodatkowe
  (`criteriaRules.maxAdditional`) wyłącznie z katalogu `config.criteria` (v1: 14 kryteriów z zał. nr 1), bez powtórzeń
  i bez dublowania obowiązkowych. Kryteria własne tylko gdy `criteriaRules.allowCustom=true` (v1: false).
  Nowy schemat (`POST /api/admin/schemas`) musi przejść walidację `CriteriaCatalog.from`.
- **Terminy**: sob./niedz./święto → następny dzień roboczy (święta stałe, Wigilia od 2025, Wielkanoc, Pn. Wielkanocny, Zielone Świątki, Boże Ciało). Nieobecność ciągła > 1 mies. przedłuża ocenę okresową o czas nieobecności (art. 81 ust. 7), nie przedłuża 6-mies. ponownej oceny. Rozmowa rozpoczynająca: 30 dni (7 po negatywnej). Sprzeciw: 7 dni od zapoznania.
- Istniejące cykle zachowują `schema_version_id` (§ 13).

## Model danych
`offices`, `users`, `schema_versions` (`config_json` JSONB), `evaluation_cycles` (+ pola procesu z V3),
`employee_notes`, `absences`, `audit_logs`. Enumy pilnowane CHECK-ami w SQL i `@Enumerated(STRING)` w JPA.
Hibernate `ddl-auto: validate` — schemat zmienia WYŁĄCZNIE Flyway.

## Zasady pracy

### Bezpieczeństwo
- Zero sekretów w repo. Hasła/klucze tylko w `infra/.env` (gitignorowany) lub zmiennych środowiskowych.
- Nowa zmienna env → dopisz do `infra/.env.example` z wartością-zaślepką.
- Przed commitem: `git status` + `git diff --cached` — sprawdź, że nie ma `target/`, `.env`, kluczy.
- Kontenery aplikacyjne działają jako użytkownik non-root.

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

### Baza / migracje
- Migracje są niezmienne po wypchnięciu — zmiana = nowy plik `V{n}__opis.sql`.
- Seed demo tylko w migracjach z `seed` w nazwie.

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
```
