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
│       │   ├── domain/       # encje JPA + enumy (Role, SheetType, CycleStatus)
│       │   ├── repository/   # Spring Data JPA
│       │   ├── auth/         # demo-login: DemoUserFilter (X-Demo-User-Id), @CurrentUser resolver
│       │   └── api/          # kontrolery REST + DTO (rekordy)
│       └── resources/
│           ├── application.yml          # konfiguracja wyłącznie przez env (DB_URL, DB_USER, DB_PASSWORD, GOTENBERG_URL)
│           └── db/migration/            # Flyway: V1__init.sql (schemat), V2__seed_demo.sql (dane demo)
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
- `GET /api/health` — status
- `GET /api/auth/demo-users` — lista użytkowników do logowania 1 kliknięciem
- `GET /api/auth/me` — bieżący użytkownik (wymaga `X-Demo-User-Id`)
- `GET /api/cycles` — cykle wg roli: KPRM_ADMIN wszystkie; HR/DG swój urząd; EVALUATOR/EVALUATED gdzie są stroną
- Swagger: `/swagger-ui.html`

## Model danych
`offices`, `users`, `schema_versions` („prawo jako kod”, `config_json` JSONB), `evaluation_cycles`,
`employee_notes`, `audit_logs`. Enumy pilnowane CHECK-ami w SQL i `@Enumerated(STRING)` w JPA.
Hibernate `ddl-auto: validate` — schemat zmienia WYŁĄCZNIE Flyway.

## Zasady pracy

### Bezpieczeństwo
- Zero sekretów w repo. Hasła/klucze tylko w `infra/.env` (gitignorowany) lub zmiennych środowiskowych.
- Nowa zmienna env → dopisz do `infra/.env.example` z wartością-zaślepką.
- Przed commitem: `git status` + `git diff --cached` — sprawdź, że nie ma `target/`, `.env`, kluczy.
- Kontenery aplikacyjne działają jako użytkownik non-root.

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
