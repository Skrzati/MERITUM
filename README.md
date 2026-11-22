# MERITUM — Cyfrowy Asystent Ocen Okresowych (KPRM)

MERITUM prowadzi pełny proces oceny okresowej członków korpusu służby cywilnej zgodnie z ustawą o służbie cywilnej
(art. 81–83, 89, 108) i rozporządzeniem w sprawie warunków i sposobu przeprowadzania ocen okresowych:
od ustalenia kryteriów i rozmowy rozpoczynającej, przez obserwację i ocenę, po zapoznanie, sprzeciw i archiwizację
arkusza w EZD RP. Reguły prawa są zapisane jako wersjonowany kod („prawo jako kod”), a AI pełni wyłącznie rolę
doradczą — za maskownicą danych osobowych i z pełnym audytem.

- **Proces 1:1 ze wzorem KPRM** — arkusze A/B/C, terminy liczone wg art. 115 KC, PDF/A-2b zgodny ze wzorem, paczka EZD
  (PDF + metadane KRI), zaproszenia `.ics`, sprzeciw i odwołanie do sądu pracy.
- **Kadry i kierownictwo** — przypisywanie cykli (pojedynczo i grupowo), zastępstwa, zaległości z drabiną eskalacji,
  alerty kadrowe (art. 71, art. 89), archiwum ocen, przypomnienia e-mail, raporty do Sprawozdania Szefa SC.
- **Wirtualny HR Partner** — przygotowanie do rozmów, sokratejskie wsparcie uzasadnienia, kontrola spójności arkusza,
  IPRZ (70-20-10) — zawsze z zastrzeżeniem AI Act i bez proponowania ocen.
- **Małe urzędy** — urząd bez dyrektora generalnego (kierownik urzędu), konta lokalne z hasłem, łączenie ról.
- **Dostępność** — WCAG 2.1 AA, 4 motywy, filtry dla daltonistów, skalowanie tekstu, `Alt+A`.

## Szybki start

```bash
make up                 # tworzy infra/.env z infra/.env.example, buduje i uruchamia kontenery
open http://localhost:3000
```

Bez `make`:

```bash
cp infra/.env.example infra/.env
docker compose -f infra/docker-compose.yml up -d --build --wait
```

Pozostałe: `make down` · `make reset` (kasuje dane, seed wgra się ponownie) · `make logs` · `make ps`.
Opcjonalny lokalny model językowy: `docker compose -f infra/docker-compose.yml --profile local-llm up -d` (Ollama).

## Adresy

| Usługa | Adres | Dostęp |
|---|---|---|
| **Aplikacja (frontend)** | **http://localhost:3000** | przeglądarka |
| Backend API (Spring Boot) | http://localhost:8080/api/health | tylko `127.0.0.1` |
| Swagger UI | http://localhost:8080/swagger-ui.html | tylko `127.0.0.1` |
| Mailpit (skrzynka testowa) | http://localhost:8025 | tylko `127.0.0.1` |
| AI Privacy Gateway | `http://ai-gateway:8000` | **wyłącznie sieć Dockera** — bez portu na hoście; AI tylko przez audytowane `/api/core-ai/*` |
| Gotenberg (PDF/A) | `http://gotenberg:3000` | wyłącznie sieć Dockera |
| PostgreSQL | `127.0.0.1:5432` | tylko lokalnie |

## Konta demo

Przy pierwszym uruchomieniu aplikacja loguje automatycznie jako kierownik (symulacja Węzła Krajowego —
`GOV_LOGIN_ENABLED=true` tylko w środowisku demo). Konto zmienia się w oknie logowania („Zmień konto”).

**Ministerstwo Cyfryzacji — Demo** (urząd z dyrektorem generalnym)

| ID | Osoba | Rola | Co pokazuje |
|---|---|---|---|
| 1 | Anna Kowalska | Dyrektor generalny | sprzeciwy, zatwierdzanie IPRZ, statystyki (wyższe stanowisko SC — bez oceny) |
| 2 | Piotr Nowak | Kierownik (oceniający) | pełny proces oceny, cykle po terminie, „Mój zespół”, HR Partner |
| 6 | Tomasz Wójcik | Kierownik (oceniający) | drugi zespół (Wydział Analiz i Danych), mapa kompetencji i mentoring |
| 3 | Katarzyna Wiśniewska | Oceniana (urzędnik SC) | potwierdzenie kryteriów, zapoznanie, sprzeciw |
| 4 | Marek Zieliński | Kadry (HR) | panel kadr, alerty, archiwum, przypomnienia, retencja |
| 5 | Ewa Lewandowska | Administrator KPRM | wersje schematu („prawo jako kod”), konta lokalne |
| 20 | Oliwia Wrona | Oceniana (umowa na czas określony) | blokada oceny okresowej (art. 37–38 w zw. z art. 35 ust. 3) |
| 21 | Grzegorz Sikora | Oceniany (urzędnik SC) | dwie oceny negatywne — alert krytyczny (art. 71 ust. 1 pkt 1) |

**Powiatowy Inspektorat Weterynarii w Płocku — Demo** (Mały Urząd: bez DG, logowanie hasłem włączone)

| ID | Osoba | Rola | Co pokazuje |
|---|---|---|---|
| 17 | Halina Sowa | Kierownik urzędu (organ rozpatrujący sprzeciw) | urząd bez DG — art. 25 ust. 9 ustawy o SC |
| 18 | Dariusz Kowal | Kierownik (oceniający) | przypomnienie kwartalne, ankieta przed rozmową |
| 19 | Kinga Lis | Oceniana | ankieta przed rozmową, prywatny dziennik |

Seed nie zawiera haseł. Aby pokazać logowanie kontem lokalnym, administrator KPRM (ID 5) ustawia hasło w `/admin` →
„Konta lokalne — tryb Mały Urząd” (min. 12 znaków, litera i cyfra), a następnie loguje się w oknie logowania
→ „Konto lokalne urzędu”. Wysłane e-maile (przypomnienia, alerty, zmiany terminów) widać w Mailpicie.

## Testy

| Moduł | Liczba testów | Polecenie |
|---|---|---|
| backend-core (JUnit 5, w tym integracyjne na PostgreSQL w Testcontainers) | **178** | `make build` (`mvn verify`, wymaga Dockera) |
| ai-gateway (pytest: PII, spójność na 10 arkuszach wzorcowych KPRM, bezpieczeństwo) | **103** | `make test-ai` |
| frontend (TypeScript + build produkcyjny Next.js) | — | `make build-fe` |
| obciążenie i scenariusze ataku na działający stos | — | `make loadtest` (potem `make reset`) |

Regresja kontroli spójności: 10 arkuszy wzorcowych KPRM — 5 celowo umieszczonych błędów wykrytych, 0 fałszywych alarmów.

## Dokumentacja

- [Wdrożenie, bezpieczeństwo, RODO/DPIA, AI Act i dostępność](docs/wdrozenie-i-bezpieczenstwo.md)
- [Analiza oficjalnych materiałów KPRM](docs/analiza-kprm.md)
- [CLAUDE.md](CLAUDE.md) — struktura repozytorium, API, maszyna stanów, reguły prawne i zasady pracy
