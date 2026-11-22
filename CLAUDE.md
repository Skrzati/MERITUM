# CLAUDE.md — MERITUM (Cyfrowy Asystent Ocen Okresowych, KPRM)

> **Zasada nadrzędna:** każda zmiana struktury katalogów, kontenerów, endpointów, migracji lub zasad
> wymaga aktualizacji tego pliku w tym samym commicie.

## Struktura repo
```
.
├── CLAUDE.md                 # ten plik — źródło prawdy o strukturze i zasadach
├── README.md                 # quickstart
├── docs/analiza-kprm.md      # analiza oficjalnych materiałów KPRM (wzory, use cases, BPMN, arkusze wzorcowe)
├── Makefile                  # make up / down / reset / logs / ps / build / loadtest
├── loadtest/                 # meritum_attack_load.py — obciążenie + scenariusze ataku na działający stos (stdlib)
├── infra/                    # wszystko do uruchamiania w Dockerze
│   ├── docker-compose.yml    # postgres, gotenberg, mailpit, backend-core, ai-gateway, frontend (sieć: backend)
│   └── .env.example          # wzór zmiennych; infra/.env jest gitignorowany
├── backend-core/             # Java 21, Spring Boot 3.3, Maven
│   ├── Dockerfile            # multi-stage: maven build -> temurin 21 JRE alpine, user non-root
│   └── src/main/
│       ├── java/pl/gov/kprm/oceny/
│       │   ├── config/       # AppConfig (Clock Europe/Warsaw), RetentionScheduler (nocne czyszczenie danych pomocniczych)
│       │   ├── domain/       # encje JPA + enumy (CycleStatus z mapą przejść, DgDecision, RescheduleMode/Status,
│       │   │                 #   ScopeChangeOutcome, HistorySource, SignatureMethod, EscalationLevel); EvaluationHistory, WellbeingSurvey, ScopeChange, RescheduleRequest,
│       │   │                 #   EvaluatorSubstitution; CycleStatus ma też mapę CORRECTIONS (tryb korekty);
│       │   │                 #   EmploymentStatus (URZEDNIK_SC / PRACOWNIK_SC_NIEOKRESLONY / _OKRESLONY / WYZSZE_STANOWISKO_SC),
│       │   │                 #   PreInterviewSurvey (ankieta przed rozmową)
│       │   ├── rules/        # czysta logika prawa, bez I/O: DeadlineService, EvaluationRuleEngine, CriteriaCatalog,
│       │   │                 #   GradeRules (art. 89 ust. 2), RescheduleRules (art. 81 ust. 8), AnonymizedCount (k-anonimowość),
│       │   │                 #   OverdueRules (dni po terminie + eskalacja), NextCycleRules (kolejny cykl),
│       │   │                 #   HrFactsRules (dni nieobecności w okresie, sygnały przeciążenia w notatkach),
│       │   │                 #   CheckinRules (przypomnienie kwartalne 90/120 dni), AppealAuthority (DG albo kierownik urzędu),
│       │   │                 #   EmploymentAlertRules (art. 71 ust. 1 pkt 1 / art. 89 ust. 2), RetentionRules (część B akt, 10/50 lat),
│       │   │                 #   SignatureStamp (SHA-256 treści arkusza + kod weryfikacyjny wydruku)
│       │   ├── service/      # CycleWorkflowService, EvaluationHistoryService, RescheduleService, ScopeChangeService,
│       │   │                 #   WellbeingService, HrService, SchemaAdminService, StatsService, PdfExportService,
│       │   │                 #   AiProxyService (audyt AI), OverdueService, NotificationService, AuditService, Access,
│       │   │                 #   IntegrationExportService (.ics, EZD), ReportExportService (CSV), SubstitutionService
│       │   │                 #   (zastępstwa), Paging (opcjonalne page/size), UserNames (imiona zbiorczo — bez N+1),
│       │   │                 #   HrAlertService (alerty kadrowe), LocalAccountService (konta lokalne BCrypt, tryb Mały Urząd),
│       │   │                 #   PreInterviewSurveyService, CycleEvents + MailNotifier (SMTP po commicie),
│       │   │                 #   WorkingNotesRetentionService (czyszczenie notatek roboczych i ankiet zamkniętych cykli),
│       │   │                 #   PrivateNoteService (prywatny dziennik — tylko właściciel), ManagementReportService
│       │   │                 #   (plan szkoleń z IPRZ, kalibracja per komórka, sprawozdanie Szefa SC)
│       │   ├── pdf/          # SheetHtmlRenderer (wzór arkusza A/B/C 1:1, SheetParts: całość / cz. I), GotenbergClient (HTML -> PDF/A-2b)
│       │   ├── export/       # czyste renderery: IcsCalendarRenderer (RFC 5545), CsvReportRenderer (BOM, „;”, <k)
│       │   ├── repository/   # Spring Data JPA
│       │   ├── auth/         # JwtService (HS256), CurrentUserFilter (Bearer JWT / X-Demo-User-Id), SecurityHeadersFilter,
│       │   │                 #   RateLimitFilter (429 dla gov-login / local-login i core-ai), WebConfig (CORS z app.cors.allowed-origins),
│       │   │                 #   @CurrentUser resolver
│       │   └── api/          # kontrolery REST, Dtos, Requests, CycleMapper, ApiExceptionHandler (RFC 7807)
│       └── resources/
│           ├── application.yml          # konfiguracja przez env (DB_*, GOTENBERG_URL, AI_GATEWAY_URL, JWT_SECRET,
│           │                            #   DEMO_HEADER_ENABLED, STATS_K_ANONYMITY, MAIL_*, RETENTION_*, BCRYPT_STRENGTH)
│           └── db/migration/            # Flyway: V1 schemat, V2 seed, V3 workflow + absences, V4 kryteria v1 (§ 4),
│                                        #   V5 pola wzoru KPRM + historia/dobrostan/zakres/terminy + opisy kryteriów + seed demo,
│                                        #   V6 delay_reason + signature_method + 2 cykle po terminie (ustalenia z mentorami),
│                                        #   V7 anonimowa ankieta dobrostanu (bez user_id, data bez godziny) + spójne uzasadnienie cyklu #9,
│                                        #   V8 users.active, evaluator_substitutions, prev_evaluation_*, mały urząd demo bez DG (Płock),
│                                        #   V9 feedback_checkins, iprz_items + iprz_status/approved/ack (art. 108) + seed IPRZ,
│                                        #   V10 employee_self_prep, V11 users.employment_status + APPEALED_TO_COURT/legal_hold/
│                                        #   appeal_round + seed (DG = wyższe stanowisko, #20 umowa okresowa, #21 dwie negatywne),
│                                        #   V12 hasła lokalne + offices.local_auth_enabled + pre_interview_surveys + schemat v2
│                                        #   (od 2027-01-01, allowCustom=true), V13 employment_start_date, job_description,
│                                        #   working_notes_purged_at, V14 criteria_expectations_json, pola podpisu
│                                        #   (signature_hash, verification_code, signed_at, paper_signed_on, scan_reference),
│                                        #   users.secondary_roles, employee_private_notes, V15 ankieta przed
│                                        #   rozmową: opens_on (kiedy oceniany ją widzi), answer_deadline, intro_message
│   └── src/test/                        # JUnit 5: rules/, pdf/, export/, auth/ (JwtAttackTest, RateLimitFilterTest), service/
│                                        #   + it/ApiIntegrationTest, it/ProcessCompletionIntegrationTest (Etap 7: przypisywanie,
│                                        #   powiadomienia, mały urząd, zastępstwa, kadry, korekta), it/SecurityIntegrationTest (IDOR, eskalacja, izolacja urzędów,
│                                        #   wstrzyknięcia), it/LegalComplianceIntegrationTest (statusy zatrudnienia, alerty, REDO →
│                                        #   ponowny sprzeciw, sąd pracy, retencja), it/SmallOfficeIntegrationTest (konta lokalne,
│                                        #   ankieta, e-maile — JavaMailSender jako @MockBean), it/ManagementAndPrivacyIntegrationTest
│                                        #   (oczekiwania, podpisy, role łączone, prywatny dziennik, raporty)
│                                        #   (Testcontainers PostgreSQL: migracje V1–V5, walidacja JPA, scenariusze API);
│                                        #   resources/docker-java.properties (api.version=1.44 dla Docker Engine 29+)
├── ai-gateway/               # Python 3.11 / FastAPI — AI & Privacy Gateway (kod zgodny z 3.9+)
│   ├── Dockerfile            # python:3.11-slim, user non-root
│   ├── app/main.py           # endpointy + modele Pydantic + BodySizeLimit (413 powyżej 64 KB, także chunked)
│   ├── app/pii.py            # mask_pii / unmask_pii / PiiMasker (OSOBA, EMAIL, PESEL, TELEFON, KOMORKA); koszt liniowy;
│                             #   WERSALIKI, nazwiska dwuczłonowe, nazwisko po funkcji („z kierownikiem Nowakiem”)
│   ├── app/llm.py            # klient OpenAI-compatible; brak klucza -> MOCK, błąd -> MOCK_FALLBACK
│   ├── app/assistants.py     # sokratejski asystent, HR-partner, przygotowanie do rozmów (+ odpowiedzi MOCK)
│   ├── app/catalog.py        # 14 kryteriów (zał. nr 1) + katalog działań rozwojowych do IPRZ
│   ├── app/criteria_suggest.py # sugestia max 3 kryteriów dodatkowych spoza obowiązkowych + oczekiwania (§ 5 ust. 1 pkt 3);
│                             #   uwzględnia job_description (opis stanowiska, art. 84) wstrzykiwany przez backend-core
│   ├── app/consistency.py    # deterministyczne reguły spójności arkusza (skalibrowane na arkuszach KPRM)
│   ├── app/guardrails.py     # brak propozycji punktów, brak treści medycznych, disclaimer AI Act
│   └── tests/                # pytest: test_pii, test_consistency, test_api, test_kprm_regression, test_suggest_criteria,
│                             #   test_hr_partner_extensions (świeżość, SBI, karty rozmów, 70-20-10, mentoring, complianceMeta),
│                             #   test_security (PII, prompt injection, walidacja, limit 64 KB, DoS — regresje luk z audytu)
│       └── fixtures/kprm_example_sheets.json  # pola 10 arkuszy wzorcowych KPRM + oczekiwane kody błędów
└── frontend/                 # Next.js 16 (App Router) + TypeScript + Tailwind 3, React 19
    ├── Dockerfile            # node:22-alpine, output standalone, user non-root
    ├── next.config.mjs       # rewrite: /api/* -> backend-core (wyliczany w czasie builda!); brak trasy do ai-gateway
    ├── tailwind.config.ts    # paleta `gov` (gov.pl) + tokeny `navy` przemapowane na niebieski gov.pl
    └── src/
        ├── app/              # strony: / (pulpit, przypisanie oceny, zastępstwa), /cycles/[id] (6 zakładek), /hr (zaległości +
        │                     #   eskalacja, raport CSV, edycja/usuwanie/anonimizacja, historia ocen, zastępstwa),
        │                     #   /stats, /audit (dziennik, filtr AI, maskedPrompt),
        │                     #   /wellbeing (ankieta + klauzula RODO), /admin (wizualny kreator schematów + konta lokalne)
        ├── components/       # AccessibilityProvider (motywy light/dark/hc-yellow/hc-bw, daltonizm, zoom 85–175%, Alt+A,
        │                     #   aria-live), AccessibilityBar (szybkie przełączniki + panel z focus trap), ColorBlindFilters
        │                     #   (feColorMatrix na #app-content-wrapper), stats/* (CalibrationCard, MentoringCard),
        │                     #   Header (pasek RP, nawigacja, dzwonek powiadomień, rola demo), LoginModal (SSO AD / Węzeł
        │                     #   Krajowy — symulacja), PrivacyNotice (RODO art. 13, AI Act art. 26/50), UserProvider,
        │                     #   AssignCycleDialog (pojedynczo z kreatorem A/B/C § 3 + podgląd terminu / grupowo),
        │                     #   RoleGate, ui.tsx (kit gov.pl, StatusBadge, VisibilityBanner, AiComplianceBadge), hr/*
        │                     #   (EmployeeEditDialog, EmployeeHistoryPanel, SubstitutionsCard, HrAlertsCard, RetentionCard,
        │                     #   IprzSummaryCard), admin/SmallOfficeAccounts (+ role łączone), cycle/* (CriteriaTab,
        │                     #   InterviewTab + PreInterviewSurveyCard, NotesTab + PrivateDiary, EvaluationTab, ChangesTab,
        │                     #   FormCorrectionPanel, AckTab + ScorePicker, ProgressSteps, context.ts)
        └── lib/              # a11y.ts (ustawienia dostępności + skrypt startowy przed hydratacją), api.ts (wyłącznie Bearer JWT
                              #   z gov-login / local-login, coreAi()), types.ts (wszystkie kontrakty DTO),
                              #   labels.ts (etykiety, roleLabel/escalationLabel z DG/kierownikiem urzędu, sheetFromAnswers,
                              #   dueState, MAX_CIVIL_SERVICE_GRADE), useAsync.ts
```

## Kontenery (`make up`)
| Kontener | Obraz | Port host | Zależy od |
|---|---|---|---|
| oceny-postgres | postgres:16-alpine | 127.0.0.1:5432 (tylko lokalnie) | — |
| oceny-gotenberg | gotenberg/gotenberg:8 | — (tylko sieć Dockera) | — |
| oceny-backend-core | build `backend-core/` | 127.0.0.1:8080 | postgres (healthy), gotenberg (healthy), mailpit; woła ai-gateway przez `/api/core-ai` |
| oceny-mailpit | axllent/mailpit:v1.21.8 | 127.0.0.1:8025 (UI skrzynki) | — (SMTP `mailpit:1025` w sieci Dockera) |
| oceny-ai-gateway | build `ai-gateway/` | — (tylko sieć Dockera; AI wyłącznie przez `/api/core-ai/*`) | — (env `LLM_BASE_URL`, `LLM_API_KEY`, `LLM_LIVE`, `LLM_MODEL`) |
| oceny-frontend | build `frontend/` | 3000 | backend-core (healthy) |
| oceny-ollama (profil `local-llm`) | ollama/ollama | — (sieć Dockera, 11434) | — opcjonalny lokalny LLM |

W sieci Dockera serwisy adresują się po nazwach (`postgres:5432`, `gotenberg:3000`).

## API (backend-core)
Uwierzytelnianie: `Authorization: Bearer <JWT>` z `POST /api/auth/gov-login` (symulacja Węzła Krajowego / Profilu
Zaufanego, HS256), `POST /api/auth/local-login` (konto lokalne urzędu — tryb Mały Urząd, hasło BCrypt) albo — dla
testów/Swaggera — `X-Demo-User-Id` (wyłączane `DEMO_HEADER_ENABLED=false`).
Publiczne: `/api/health`, `/api/auth/demo-users` (bez e-maili), `/api/auth/gov-login`, `/api/auth/local-login`. Każda odpowiedź ma nagłówki
`X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: strict-origin-when-cross-origin`
(+ `Cache-Control: no-store` dla `/api`).
Błędy: 400 złe parametry (np. stronicowanie), 403 brak uprawnień, 404 brak zasobu, 409 niedozwolone przejście stanu,
413 za duże żądanie AI (> `app.ai.max-request-bytes`, 64 KB), 422 naruszenie reguł (`violations`, opcjonalnie `result`),
429 rate-limit (`Retry-After`; okno 60 s: `POST /api/auth/*` (gov-login, local-login) 30 / adres klienta, `core-ai` 60 / użytkownik — `app.rate-limit.*`).
Stronicowanie (opcjonalne, wstecznie zgodne): `GET /api/cycles`, `/api/hr/employees`, `/api/audit-logs` przyjmują
`page` (od 0) i `size` (1–200); bez parametrów — pełna lista (audyt: `limit`).

| Metoda | Ścieżka | Kto | Efekt |
|---|---|---|---|
| GET | `/api/health` | wszyscy | status |
| GET | `/api/auth/demo-users`, `/api/auth/me` | wszyscy | logowanie demo; demo-users → `DemoUserDto` (bez e-maila i dat kadrowych) |
| POST | `/api/auth/gov-login` | wszyscy | `{"userId"}` → `{accessToken, tokenType: "Bearer", expiresIn, identityProvider, user}` |
| POST | `/api/auth/local-login` | wszyscy | `{login (e-mail), password}` → jak gov-login (`identityProvider: LOCAL`); tylko aktywne konto z hasłem w urzędzie z `local_auth_enabled`; 401 bez rozróżnienia przyczyny, audyt `LOGIN_FAILED` |
| GET | `/api/admin/offices`, PATCH `/api/admin/offices/{id}/local-auth` | KPRM_ADMIN (HR: GET własny) | urzędy; `{enabled}` włącza tryb Mały Urząd (audyt `LOCAL_AUTH_TOGGLED`) |
| GET/POST | `/api/admin/accounts[?officeId=]` | KPRM_ADMIN (dowolny urząd, `officeId` wymagany), HR (własny) | `AccountDto {user, hasLocalPassword, passwordChangedAt}`; POST `{officeId?, firstName, lastName, email, role (bez KPRM_ADMIN), employmentStatus?, supervisorId?, department?, positionTitle?, password?}`; audyt `ACCOUNT_CREATED` |
| POST | `/api/admin/accounts/{id}/password`, `/api/admin/accounts/import-csv?officeId=` | jw. | hasło min. 12 znaków z literą i cyfrą (422), audyt `PASSWORD_SET`; import CSV jak `/api/hr/import-csv` do wskazanego urzędu |
| GET | `/api/cycles`, `/api/cycles/{id}` | wg roli | KPRM_ADMIN wszystko; HR/DG urząd; strony cyklu; pola `criteriaDeadline`, `mandatoryGradeEligible`, `mandatoryGradeDeadline`, `overdueDays`, `delayReason`, `signatureMethod` |
| POST | `/api/cycles` | HR / DG / bezpośredni przełożony | `{employeeId, sheetType?, startDate?, reevaluation?, evaluatorId?, place?}` — **zawsze najnowsza obowiązująca wersja schematu**, termin z jej `period_months` (6 mies. przy ponownej ocenie); oceniający domyślnie `supervisorId`, miejscowość — miasto urzędu; 422 dla `PRACOWNIK_SC_OKRESLONY` (pierwsza ocena, art. 37–38 w zw. z art. 35 ust. 3) i `WYZSZE_STANOWISKO_SC` (`bulk` — pominięcie z tym powodem) |
| POST | `/api/cycles/bulk` | HR, DG | `{employeeIds?, department?, startDate?}` → `{created: [CycleDto], skipped: [{employeeId, reason}]}` (max 500; pomija osoby z aktywnym cyklem / bez przełożonego) |
| GET | `/api/cycles/due-preview?employeeId=&startDate=&reevaluation=` | HR / DG / przełożony (urząd) | `{dueDate, criteriaDeadline, periodMonths, schemaVersion, suggestedSheet, defaultEvaluatorId, defaultPlace, employmentStatus, blockedReason}` |
| PATCH | `/api/cycles/{id}/details` | oceniający / zastępca / HR, cykl aktywny | `{place?, startDate?, dueDate?, reason?}` — zmiana okresu po potwierdzeniu kryteriów wymaga `reason` (422); audyt `CYCLE_DETAILS_UPDATED` |
| POST | `/api/cycles/{id}/admin-correction` | HR / DG (urząd), KPRM_ADMIN | tryb korekty: `{reason (wymagane), targetStatus?, criteriaInterviewDate?, criteriaConfirmedOn?, evaluationInterviewDate?, evaluatedOn?, acknowledgedAt?, place?, startDate?, dueDate?}`; audyt `ADMIN_CORRECTION` (from/to + zmiany pól) |
| GET | `/api/cycles/{id}/history` | jak `GET /api/cycles/{id}` | historia ocen ocenianego (`SYSTEM` / `HISTORICAL`) |
| GET/POST | `/api/cycles/{id}/checkins` | zapis: oceniający/zastępca; odczyt: oni + oceniany (tylko `sharedWithEmployee`) | bieżąca informacja zwrotna; resetuje `QUARTERLY_CHECKIN`; audyt `CHECKIN_ADDED` |
| GET/POST/PATCH/DELETE | `/api/cycles/{id}/iprz-items[/{itemId}]` | odczyt: jak cykl; zapis: oceniający/zastępca/HR | pozycje IPRZ `{criterionCode, actionTitle, formType, targetDate, status}`; po zatwierdzeniu tylko `status` |
| POST | `/api/cycles/{id}/iprz/approve`, `/iprz/ack` | DG (urząd) / oceniany | art. 108: zatwierdzenie (`IPRZ_APPROVED`) i zapoznanie (`IPRZ_ACKNOWLEDGED`) |
| GET | `/api/cycles/{id}/ezd-zip` | jak `GET /api/cycles/{id}` | `application/zip`: `arkusz-oceny-{id}.pdf` (PDF/A-2b) + `metadane-ezd.json`; audyt `EZD_ZIP_EXPORTED` |
| GET/PUT | `/api/cycles/{id}/self-prep` | PUT: tylko oceniany; GET: oceniany, oceniający/zastępca (treść tylko gdy `sharedWithEvaluator`), inni 403 | przygotowanie ocenianego `{achievements, challenges, developmentNeeds, sharedWithEvaluator}` |
| GET | `/api/team/overview` | EVALUATOR, DG | „Mój zespół”: mapa kompetencji (ostatnie oceny cząstkowe) + średnie, mentoring (mentor 7–9 / mentee ≤ 5), sygnały (`HrFactsRules`) — zapytania zbiorcze |
| POST | `/api/cycles/{id}/next` | HR / oceniający | tylko z `COMPLETED`: po NEGATIVE ponowna ocena (`startDate = acknowledgedAt`, +6 mies.), po POSITIVE zwykły cykl od daty oceny; zawsze najnowszy schemat |
| POST | `/api/cycles/{id}/criteria` | oceniający | `{"additional":[{"code":"K07"}], "interviewDate"?, "expectations"?: {"K07": "oczekiwania na poziomie 5 pkt"}}` (klucze wyłącznie z kryteriów arkusza, inaczej 422; zwracane jako `criteriaExpectations`) — obowiązkowe wg arkusza + 0–3 z katalogu (status DRAFT_CRITERIA) |
| POST | `/api/cycles/{id}/confirm-criteria` | oceniany | → OBSERVATION (kryteria zablokowane) |
| GET/POST | `/api/cycles/{id}/notes` | oceniający (POST); oceniany widzi swoje + `isSharedAfterEval` po ocenie | |
| PUT | `/api/cycles/{id}/evaluate` | oceniający | `submit=false` → EVALUATION_DRAFT; `submit=true` → walidacja § 11 → AWAITING_ACK (+ `evaluationInterviewDate`, `delayReason`, `signatureMethod`); **ocena po terminie nie jest blokowana** — audyt `EVALUATION_OVERDUE` |
| POST | `/api/cycles/{id}/acknowledge` | oceniany | opcjonalnie `{signatureMethod, paperSignedOn?, scanReference?}` — tryby `QUALIFIED_OR_TRUSTED` (QES / Profil Zaufany / mObywatel), `INTERNAL_AUDIT_STAMP`, `HYBRID_PAPER_SCAN` (wymaga `paperSignedOn`, 422); dawne `MOBYWATEL_QES\|PAPER_HYBRID\|SYSTEM_DEMO` (domyślnie SYSTEM_DEMO) mapowane przez `SignatureMethod.mode()` → COMPLETED, termin sprzeciwu +7 dni; zawsze `signatureHash` (SHA-256), `signatureVerificationCode` (drukowany na PDF), `signedAt` i audyt z `sha256` |
| POST | `/api/cycles/{id}/refusal` | oceniający / HR | fikcja zapoznania (powód, data) → COMPLETED, +7 dni na sprzeciw |
| POST | `/api/cycles/{id}/appeal` | oceniany | → APPEALED (w terminie); `appealDecisionDeadline` = +14 dni (art. 83 ust. 3); po REDO ponowny sprzeciw (art. 83 ust. 4, czyści poprzednią decyzję); po CHANGE 409 — tylko sąd pracy (`appealAllowed=false`) |
| POST | `/api/cycles/{id}/appeal/decision` | DG | `appealRound`+1; CHANGE (nowe oceny) → COMPLETED (zdarzenie `CycleClosed`; ocena negatywna → e-mail z terminem ponownej oceny); REDO → EVALUATION_DRAFT, nowy `dueDate` = max(bieżący, decyzja + 14 dni), czyści datę rozmowy/podpis (.ics z `SEQUENCE = appealRound`), e-mail |
| POST | `/api/cycles/{id}/court-appeal`, `/court-appeal/close` | HR / DG (urząd) | art. 83 ust. 5: `{filedOn, caseReference?}` COMPLETED → APPEALED_TO_COURT, `legalHold=true` (audyt `COURT_APPEAL_REGISTERED`); `{outcome}` → COMPLETED, `legalHold=false` |
| GET/POST/DELETE/PUT | `/api/cycles/{id}/pre-interview-survey`, `/questions[/{qid}]`, `/answers`, POST `/send` | odczyt: oceniany, oceniający/zastępca (odpowiedzi po wysłaniu), HR urzędu (bez odpowiedzi); pytania własne: przełożony / HR (DRAFT, `allowCustom` ze schematu, max 10); odpowiedzi i „Wyślij przełożonemu”: oceniany — dopiero po udostępnieniu (`open`, inaczej 409; oceniany dostaje pustą listę pytań) | ankieta przed rozmową niezależna od dobrostanu; audyt `SURVEY_QUESTION_ADDED`, `PRE_INTERVIEW_SURVEY_SENT` |
| PUT | `/api/cycles/{id}/pre-interview-survey/settings` | oceniający / zastępca (ankieta DRAFT, cykl aktywny) | `{opensOn (null = nieudostępniona, ≤ dziś = od razu), answerDeadline? (≥ opensOn), introMessage? (≤ 2000)}` → widok z `open`, `canSchedule`; wycofanie (null) tylko, gdy oceniany nie zapisał odpowiedzi (422); audyt `PRE_INTERVIEW_SURVEY_SCHEDULED` |
| GET/POST | `/api/cycles/{id}/reschedule` | oceniający (pkt 1–3) / oceniany (pkt 4) | art. 81 ust. 8: `{mode: EVALUATOR_CHANGE\|EMPLOYEE_ABSENCE\|EVALUATOR_ABSENCE\|EMPLOYEE_REQUEST, newDueDate, reason}`; warunek > 6 mies.; zawiadomienie § 8 w `noticeText` |
| POST | `/api/cycles/{id}/reschedule/{requestId}/decision` | oceniający | `{accept, comment}` — decyzja w sprawie wniosku ocenianego |
| GET/POST | `/api/cycles/{id}/scope-changes` | HR / oceniający | `{changeDate, newPositionTitle?, description, significant}` → `NONE` \| `CRITERIA_RESET` (§ 9 ust. 1) \| `EVALUATION_REQUIRED` (art. 81 ust. 6) |
| GET | `/api/cycles/{id}/interview.ics` | jak `GET /api/cycles/{id}` | `text/calendar` (RFC 5545, czas UTC): rozmowa rozpoczynająca przy `DRAFT_CRITERIA` (§ 5 ust. 1), w pozostałych etapach oceniająca (§ 10); data rozmowy z cyklu albo termin kryteriów / oceny, 10:00, 60 min |
| GET | `/api/cycles/{id}/ezd-package` | jak `GET /api/cycles/{id}` | metadane KRI/EZD RP: `caseId`, `title`, `documentType`, `sheetType`, `office`, `parties`, `status`, `evaluatedOn`, `acknowledgedAt`, `signatureMethod`, `format`, `access`, `legalBasis`, `pdfEndpoint`, `schemaVersion`, `generatedAt`, `retention {personnelFilePart: B, years 10/50, category, basis, legalHold, retainUntil}`, `archiveContents`, `excludedFromArchive` (EmployeeNote, PreInterviewSurvey, EmployeeSelfPrep), `exclusionReason` |
| GET | `/api/cycles/{id}/hr-facts` | jak `GET /api/cycles/{id}`, ale **nie oceniany** (D4) | fakty z bazy dla HR Partnera: `{absenceDays, scopeChangesCount, overloadNotesCount, totalNotesCount, daysSinceLastNote, overdueDays}` |
| GET | `/api/hr/reports/export` | HR, DG | CSV (UTF-8 z BOM, `;`) — te same agregaty co `/api/stats/summary` + eskalacja; liczności 1..k-1 jako `<k`; audyt `REPORT_EXPORTED` |
| GET/POST/PUT/DELETE | `/api/private-notes[/{id}]?cycleId=` | wyłącznie właściciel (`ownerId == current`), inni 403 | prywatny dziennik pracownika `{cycleId? (tylko własny cykl), content ≤ 10 000}`; bez audytu treści, poza aktami, EZD i eksportem RODO kadr |
| PATCH | `/api/admin/accounts/{id}/roles` | KPRM_ADMIN, HR (urząd) | `{secondaryRoles}` — łączenie ról w małym urzędzie (np. DG + EVALUATOR + HR); KPRM_ADMIN → 403; audyt `SECONDARY_ROLES_SET` |
| GET | `/api/hr/iprz-summary` | HR, DG | plan szkoleń z zatwierdzonych IPRZ: `{approvedPlans, byCriterion[{criterionCode, criterionName, actions, employees}], byFormType, topActions}` |
| GET | `/api/stats/calibration?year=` | HR, DG | per komórka: `evaluations`, `averageScore`, rozkład poziomów i wyników (`{value, suppressed}`); komórki z < k ocenami pominięte (`suppressedDepartments`) |
| GET | `/api/hr/export/ssc-report.csv?year=` | HR, DG | CSV do Sprawozdania Szefa SC: oceny (sporządzone / pozytywne / negatywne), sprzeciwy wniesione i uwzględnione (APPEAL_FILED / APPEAL_DECIDED), odwołania do sądu, wnioski z art. 89, przypadki art. 71 ust. 1 pkt 1 — liczby dokładne (sprawozdawczość urzędu); audyt `SSC_REPORT_EXPORTED` |
| GET | `/api/hr/alerts` | HR, DG | alerty z historii + bieżącej oceny (AWAITING_ACK): `TWO_NEGATIVE_TERMINATION` (CRITICAL, urzędnik SC, art. 71 ust. 1 pkt 1), `TWO_NEGATIVE_INFO` (pracownik SC na czas nieokreślony), `MANDATORY_GRADE` (art. 89 ust. 2); krytyczne pierwsze |
| POST | `/api/hr/retention/purge-working-notes` | HR | czyszczenie danych pomocniczych zamkniętych cykli urzędu (COMPLETED, bez legal hold, po `RETENTION_GRACE_DAYS` od zamknięcia): notatki, ankiety przed rozmową, self-prep → `{cyclesPurged, notesDeleted, surveysDeleted, skippedLegalHold}`; arkusz, historia i audyt zostają; audyt `WORKING_NOTES_PURGED` |
| GET | `/api/hr/overdue` | HR, DG | aktywne cykle po terminie (bez sporządzonej oceny), malejąco wg `overdueDays`, z `escalationLevel`: 1–7 OCENIAJACY, 8–14 ZASTEPCA, 15–30 KADRY, > 30 DYREKTOR_GENERALNY |
| GET | `/api/notifications` | zalogowany | wyliczane w locie dla widocznych cykli: `OVERDUE`, `DUE_SOON` (≤ 30 dni), `CRITERIA_DUE`, `APPEAL_DECISION_DUE`, `MANDATORY_GRADE_DUE`; oceniający/zastępca: `QUARTERLY_CHECKIN` (OBSERVATION, ≥ 90 dni bez aktywności MEDIUM, ≥ 120 HIGH), `RESCHEDULE_PENDING`; HR/DG: `GRADE_REQUEST_SUBMITTED` (30 dni od sporządzenia); oceniany i oceniający: `APPEAL_DECIDED` (30 dni); HR/DG: `HR_CRITICAL_ALERT` / `HR_INFO_ALERT` (`cycleId` może być null); oceniający: `PRE_INTERVIEW_SURVEY_SENT` (30 dni); oceniany: `PRE_INTERVIEW_SURVEY_OPEN` (udostępniona, niewysłana; `dueDate` = termin odpowiedzi); `{type, severity, cycleId, dueDate, message}` |
| GET/POST | `/api/substitutions` | GET: HR/DG — urząd, pozostali — własne; POST: HR | `{evaluatorId, deputyId, startDate, endDate, reason}` → `SubstitutionDto` (`activeToday`) |
| DELETE | `/api/substitutions/{id}` | HR | zakończenie (przyszłe — usunięte, trwające — koniec wczoraj) |
| GET | `/api/admin/schemas/{id}/package` | KPRM_ADMIN | pakiet reguł `meritum-rules-<versionCode>.json` (`format`, `versionCode`, `effectiveFrom`, `periodMonths`, `config`) |
| GET | `/api/audit-logs?limit=&page=&size=` | HR, DG (urząd), KPRM_ADMIN (wszystko) | ostatnie zdarzenia audytowe |
| POST | `/api/wellbeing` | każdy | `{workload, energy, support (1–5), wantsConversation}` — technicznie anonimowa (bez `user_id`, data bez godziny), bez audytu, tylko agregaty |
| POST | `/api/core-ai/{socratic-draft\|consistency-check\|hr-partner\|interview-prep\|suggest-criteria\|appreciation-nudge\|team-mentoring}?cycleId=` | zalogowany; wszystkie poza `consistency-check` — tylko EVALUATOR / DG / HR | proxy do ai-gateway + wpis `AI_*` w `audit_logs` (tryb LLM, `maskedPrompt` przycięty do 4000 znaków, kody ostrzeżeń) — AI Act art. 26; `suggest-criteria` z `cycleId` dostaje `job_description` z profilu ocenianego (nadpisuje wartość z przeglądarki) |
| GET | `/api/hr/employees?q=&sort=&dir=&page=&size=` | HR | lista pracowników urzędu |
| POST | `/api/hr/absences` | HR | rejestracja nieobecności, przeliczenie `due_date` |
| POST | `/api/hr/handover` | HR | zmiana przełożonego (§ 9 ust. 3) |
| PATCH | `/api/hr/employees/{id}` | HR | imię, nazwisko, komórka, stanowisko, rola (bez KPRM_ADMIN), `employmentStatus` (albo dawne `civilServant`), stopień 1–9, rok mianowania, data stopnia, przełożony (= handover), `jobDescription` (art. 84, ≤ 10 000 zn.), `employmentStartDate` (nie z przyszłości); audyt `EMPLOYEE_UPDATED` (tylko nazwy pól) |
| DELETE | `/api/hr/employees/{id}` | HR | `{outcome: DELETED}` gdy brak śladów w procesie; inaczej `ANONYMIZED` (dane osobowe, notatki robocze i nieobecności usunięte, `active=false`, arkusze/historia/audyt zachowane); 409 przy aktywnym cyklu lub podwładnych |
| GET/POST | `/api/hr/employees/{id}/history` | HR | historia ocen; POST `{outcome, level, evaluatedOn}` — ocena sprzed wdrożenia (`HISTORICAL`) |
| POST | `/api/hr/import-csv` (multipart `file`) | HR | `full_name,email,role,is_civil_servant\|employment_status[,supervisor_email]` (true/tak = URZEDNIK_SC) |
| GET | `/api/hr/employees/{id}/gdpr-export` | HR | art. 15 RODO (cykle, notatki, nieobecności, historia ocen, audyt; ankiety są anonimowe — nie wchodzą) |
| GET/POST | `/api/admin/schemas` | KPRM_ADMIN | wersje schematu („prawo jako kod”) |
| GET | `/api/schemas/{id}` | każdy zalogowany | odczyt wersji schematu (katalog kryteriów dla UI) |
| GET | `/api/stats/summary` | DG, HR | tylko agregaty; każda liczność to `{value, suppressed}` — 1..k-1 → `{null, true}` (k z `STATS_K_ANONYMITY`, domyślnie 5); `wellbeing` (średnie tylko przy ≥ k odpowiedziach) |
| GET | `/api/cycles/{id}/pdf[?parts=1]` | jak `GET /api/cycles/{id}` | PDF/A arkusza 1:1 wg wzoru (cz. I–III, średnia z przecinkiem, X w tabeli przyznania oceny, pouczenie art. 83 ust. 1); cz. III tylko urzędnik SC + ocena pozytywna + wniosek; `parts=1` — wyłącznie cz. I (kryteria + podpisy po rozmowie rozpoczynającej), inne wartości niż `1`/`all` → 400; audyt `PDF_EXPORTED` (`parts`) |

## API (ai-gateway, :8000 — tylko w sieci Dockera; żądania > 64 KB → 413)
| Metoda | Ścieżka | Efekt |
|---|---|---|
| GET | `/health` | status, `llm_mode` (LIVE / MOCK) |
| POST | `/api/ai/socratic-draft` | `masked_prompt`, `socratic_questions`, `draft_justification`, `ai_disclaimer` — nigdy nie proponuje punktów |
| POST | `/api/ai/consistency-check` | opcjonalnie `notes[{date, text}]`, `periodEnd`, `recentScopeChange` → `warnings[{code, severity, message}]`, `auto_modified: false` |
| POST | `/api/ai/hr-partner` | `masked_prompt`, `burnout_risk_signal`, `conversation_starters`, `organizational_suggestions`, `appreciation_moment` |
| POST | `/api/ai/interview-prep` | `{stage: INITIAL\|EVALUATION\|FEEDBACK, difficultScenario?: SCORE_DROP\|NEGATIVE_EVALUATION\|OVERLOAD_SIGNALS}` (FEEDBACK — FUKO + docenienie z notatek) → `goal`, `agenda[{topic, legal_basis, questions}]` (§ 5 ust. 1: 4 zagadnienia / § 10: 3), `difficult_situations`, `development_catalog` (IPRZ, zawsze `onTheJob70`/`social20`/`formal10`, `estimatedCostPln: 0`), `difficult_conversation {structure, questions, role_boundaries}` (stała treść karty) |
| POST | `/api/ai/suggest-criteria` | `{position_title, department, sheet_type, mandatory_codes, responsibilities_summary, job_description?}` → `masked_prompt`, `suggestions[{code, name, reason, expectations}]` (max 3, wyłącznie z katalogu, spoza obowiązkowych — filtr także na odpowiedzi LLM), `ai_disclaimer`, `mode` |
| POST | `/api/ai/appreciation-nudge` | `{employee_name?, observations, completedTasks}` → `masked_prompt`, SBI `situation`/`behavior`/`impact` + `message` (docenienie przed oceną) |
| POST | `/api/ai/team-mentoring` | `{members[{alias, strongCriteria, developmentAreas}]}` (2–200, kody K.. lub nazwy) → `pairs[{mentor, mentee, criterion, rationale}]` (reguły, mentor max 2 osoby), `unmatched`, `mode: RULES` |

Każda odpowiedź endpointów `/api/ai/*` ma `complianceMeta {piiAnonymized, redactedCount (liczba zamaskowanych wystąpień —
`PiiMasker.redacted_count`), humanInTheLoopRequired, advisoryOnly, disclaimer}` (AI Act, zał. III pkt 4).
Kody `consistency-check`: `CRITERIA_MISMATCH`, `FOREIGN_NAME`, `LEVEL_MISMATCH`, `AVERAGE_MISMATCH`, `DUPLICATE_TEXT`, `GENDER_MISMATCH`,
`RECENCY_BIAS` (≥ 3 notatki, > 75% z ostatnich 90 dni okresu), `RECENT_SCOPE_CHANGE` (przypomnienie o okresie wdrożenia).
Frontend woła AI wyłącznie przez `/api/core-ai/*?cycleId=` (audyt), nie bezpośrednio `/api/ai/*`.
| — | `/swagger-ui.html` | | dokumentacja |

## Maszyna stanów
```
DRAFT_CRITERIA → OBSERVATION → EVALUATION_DRAFT ⟲ → AWAITING_ACK → COMPLETED → APPEALED
                 (OBSERVATION → AWAITING_ACK bezpośrednio)          APPEALED → COMPLETED (CHANGE) | EVALUATION_DRAFT (REDO)
                 OBSERVATION → DRAFT_CRITERIA (istotna zmiana zakresu obowiązków, § 9 ust. 1)
                                                                    COMPLETED ⇄ APPEALED_TO_COURT (art. 83 ust. 5, legal hold)
```
APPEALED_TO_COURT nie jest stanem aktywnym i nie podlega trybowi korekty.
Przejścia zdefiniowane w `CycleStatus.TRANSITIONS`; każda zmiana stanu → wpis w `audit_logs`.
Tryb korekty (`CycleStatus.CORRECTIONS`, tylko wstecz, z uzasadnieniem): OBSERVATION → DRAFT_CRITERIA, EVALUATION_DRAFT →
OBSERVATION, AWAITING_ACK → EVALUATION_DRAFT, COMPLETED → AWAITING_ACK | EVALUATION_DRAFT (usuwa wpis historii, unieważnia
zapoznanie/sporządzenie); nie może otworzyć drugiego aktywnego cyklu pracownika.
Zakończenie (zapoznanie, fikcja zapoznania, zmiana oceny przez DG) zapisuje wpis `evaluation_history` (SYSTEM); REDO go usuwa.

## Reguły prawne (`rules/`)
- **Status zatrudnienia** (`EmploymentStatus`, kolumna `users.employment_status`; NULL → wyprowadzany z
  `is_civil_servant`, który jest utrzymywany jako pochodna): ocenie okresowej podlegają `URZEDNIK_SC` i
  `PRACOWNIK_SC_NIEOKRESLONY`. `PRACOWNIK_SC_OKRESLONY` (umowa 12 mies.) ma **pierwszą ocenę z art. 37–38 w zw. z art. 35
  ust. 3** (nie art. 81 ust. 3 — ten, jak art. 84, dotyczy opisu stanowiska), `WYZSZE_STANOWISKO_SC` — bez oceny okresowej.
  Wniosek o stopień (art. 89) i cz. III arkusza wyłącznie dla `URZEDNIK_SC` (`civilServant` = pochodna statusu).
- **Alerty kadrowe** (`EmploymentAlertRules`): dwie ostatnie oceny (historia + bieżąca sporządzona) negatywne — urzędnik SC
  → CRITICAL (art. 71 ust. 1 pkt 1, e-mail do HR i DG urzędu po zamknięciu cyklu), pracownik SC (nieokreślony) → INFO;
  dwie kolejne na dwóch najwyższych poziomach u urzędnika SC → `MANDATORY_GRADE` (art. 89 ust. 2).
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
  po zdarzeniu; sob./niedz./święto → następny dzień roboczy (art. 115 KC) (święta stałe, Wigilia od 2025, Wielkanoc, Pn. Wielkanocny,
  Zielone Świątki, Boże Ciało). Kolejne nieobecności rozdzielone wyłącznie dniami wolnymi = jedna nieobecność ciągła
  (`mergeContinuous`). Nieobecność ciągła > 1 mies. przedłuża ocenę okresową (art. 81 ust. 7), nie przedłuża 6-mies.
  ponownej oceny (art. 81 ust. 9). Kryteria: 30 dni (7 po negatywnej, § 7). Sprzeciw: 7 dni (art. 83 ust. 1); decyzja DG:
  14 dni (art. 83 ust. 3); obligatoryjny stopień: 30 dni od zapoznania (art. 89 ust. 2).
- **Zmiana terminu** (art. 81 ust. 8): 4 przesłanki; dopuszczalna, gdy od ostatniej oceny minęło > 6 mies. (pierwsza ocena:
  okres oceny > 6 mies.); pisemne zawiadomienie (§ 8) generowane w `noticeText`.
- **Terminy nie blokują** (ustalenie z mentorami KPRM): ocena po terminie jest dopuszczalna; rejestrujemy `delay_reason`,
  wpis `EVALUATION_OVERDUE` i eskalację (`OverdueRules`). PDF pokazuje przyczynę opóźnienia i adnotację QES.
- **Obligatoryjny stopień** (art. 89 ust. 2, `GradeRules`): urzędnik SC, po ostatnim stopniu dwie kolejne oceny pozytywne na
  poziomie „powyżej” / „znacznie powyżej oczekiwań”, stopień < 9 → `mandatoryGradeEligible`.
- Istniejące cykle zachowują `schema_version_id` (§ 13).
- **Prawo jako kod 2.0** (`SchemaRules`): opcjonalne `config.deadlines` (`initialInterviewDays`, `initialInterviewAfterNegativeDays`,
  `appealDays`, `appealDecisionDays`, `reevaluationMonths`) i `config.levelThresholds` (5 rosnących górnych progów, ostatni 9);
  brak/błąd przy odczycie → wartości z 2016 r.; nowa wersja z błędnymi wartościami → 422. Terminy i poziomy cyklu liczone
  z jego wersji schematu (cache per wersja — wersje są niezmienne).
- **Sprzeciw po REDO** (art. 83 ust. 4): ocenę sporządza się po raz drugi i przysługuje od niej ponowny sprzeciw; termin
  ponownego sporządzenia `DeadlineService.redoEvaluationDue` = max(bieżący termin, decyzja + 14 dni) — ustawa nie określa
  terminu (założenie organizacyjne). Od oceny zmienionej (CHANGE) — tylko odwołanie do sądu pracy (art. 83 ust. 5).
  Powiadomienia są wyliczane w locie, więc decyzja usuwa `APPEAL_DECISION_DUE` i przelicza `OVERDUE` / `DUE_SOON`.
- **Retencja** (`RetentionRules`): arkusz oceny to część B akt osobowych — nigdy nie jest usuwany automatycznie (także przy
  anonimizacji RODO): 10 lat (stosunek pracy od 1.01.2019), 50 lat (wcześniej lub brak `employment_start_date`), legal hold
  przy sporze sądowym. Czyszczone są wyłącznie dane pomocnicze zamkniętych cykli (notatki robocze, ankiety przed rozmową,
  self-prep) — jawnie wyłączone z paczki archiwalnej EZD (zakaz „czarnych teczek”).
- **Role łączone** (`users.secondary_roles`): `Access.requireRole` i kontrole w serwisach używają `User.hasRole` (rola główna
  lub dodatkowa); widoczność cykli wg najszerszej roli (KPRM_ADMIN > HR > DG). KPRM_ADMIN nie może być rolą dodatkową.
- **Mały urząd bez DG** (`offices.has_dg_role = false`, `AppealAuthority`): sprzeciw rozpatruje kierownik urzędu (użytkownik z
  rolą DG); PDF (pouczenie art. 83 ust. 1), `CycleDto.appealAuthority`, etykieta roli, drabina eskalacji > 30 dni i komunikaty
  w UI używają nazwy organu dynamicznie.
- **Zastępstwa**: aktywny zastępca (dziś ∈ [start, end]) widzi aktywne cykle zastępowanego, działa jak oceniający
  (`canActAsEvaluator`), dostaje powiadomienia; jego wpisy audytu mają `substituteFor`.
- **Przypomnienia kwartalne** (`CheckinRules`): ostatnia aktywność = max(start, potwierdzenie kryteriów, ostatnia notatka,
  ostatni check-in).
- **Urząd bez DG**: pouczenie w PDF dopisuje podstawę „art. 25 ust. 9 ustawy o służbie cywilnej” (`AppealAuthority.legalBasis`).
- **IPRZ** (art. 108): DRAFT → PROPOSED (pierwsza pozycja) → APPROVED (DG / kierownik urzędu); pozycje drukowane w PDF.
- **Nieobecność a zmiana terminu**: przeliczenie z nieobecności nie cofa terminu wcześniej niż zastosowany (APPLIED) wniosek
  z art. 81 ust. 8 — zachowywana późniejsza data, audyt `keptRescheduledDueDate`.
- **Poprzednia ocena na arkuszu**: z historii (`SYSTEM`/`HISTORICAL`), a gdy brak — wpisana ręcznie w ocenie
  (`previousEvaluation`, źródło `MANUAL`).

## Model danych
`offices` (+ `city`), `users` (+ `first_name`, `last_name`, `department`, `position_title`, `appointment_year`,
`civil_service_grade`, `last_grade_date`), `schema_versions` (`config_json` JSONB: kryteria z opisami, `mandatoryBySheet`),
`evaluation_cycles` (+ pola procesu z V3, daty rozmów, `evaluated_on`, `place`, `appeal_decision_deadline`, `delay_reason`,
`signature_method`, `prev_evaluation_outcome|level|date`), `evaluator_substitutions`, `users.active`,
`evaluation_history`, `employee_notes`, `absences`, `reschedule_requests`, `scope_changes`, `wellbeing_surveys`, `audit_logs`;
V11–V13: `users.employment_status|password_hash|password_changed_at|employment_start_date|job_description`,
`offices.local_auth_enabled`, `evaluation_cycles.legal_hold|court_appeal_filed_on|court_case_reference|appeal_round|
working_notes_purged_at`, `pre_interview_surveys` (pytania + odpowiedzi w JSONB, status DRAFT/SENT); schemat `v2`
(od 2027-01-01) = v1 + `criteriaRules.allowCustom=true` + `preInterviewSurvey.allowCustom=true` (v1 bez zmian).
Seed demo (V5): 16 użytkowników, 15 cykli we wszystkich statusach (w tym 2 osoby uprawnione z art. 89 ust. 2, ponowna ocena,
sprzeciw, oczekujący wniosek o zmianę terminu), 7 ankiet dobrostanu; V6: cykle 16–17 po terminie (eskalacja); V8: urząd #2
„Powiatowy Inspektorat Weterynarii w Płocku — Demo” bez DG (użytkownicy 17 kierownik urzędu/DG, 18 naczelnik, 19 specjalista;
cykl 18 w obserwacji, ostatnia notatka 15.07.2025 → przypomnienie kwartalne); V11: DG (#1) = wyższe stanowisko SC,
#20 umowa na czas określony, #21 urzędnik z dwiema ocenami negatywnymi (alert krytyczny); V12: urząd Płock z trybem
Mały Urząd (bez seedowanych haseł — ustawia je administrator); V13: daty zatrudnienia i opisy stanowisk. Enumy pilnowane CHECK-ami w SQL i `@Enumerated(STRING)` w JPA.
Hibernate `ddl-auto: validate` — schemat zmienia WYŁĄCZNIE Flyway.

## Zasady pracy

### Bezpieczeństwo
- Zero sekretów w repo. Hasła/klucze tylko w `infra/.env` (gitignorowany) lub zmiennych środowiskowych.
- Nowa zmienna env → dopisz do `infra/.env.example` z wartością-zaślepką.
- Przed commitem: `git status` + `git diff --cached` — sprawdź, że nie ma `target/`, `.env`, kluczy.
- Kontenery aplikacyjne działają jako użytkownik non-root.
- JWT podpisywany HMAC-SHA256 kluczem z `JWT_SECRET` (min. 32 bajty); weryfikacja w czasie stałym, kontrola `exp` i `iss`.
- Statystyki: k-anonimizacja egzekwowana w backendzie — surowe liczności 1..k-1 nie trafiają do JSON.
- Konta lokalne: hasło wyłącznie jako skrót BCrypt (`BCRYPT_STRENGTH`, domyślnie 12), nigdy w DTO ani audycie; logowanie
  porównuje z fikcyjnym skrótem, gdy konta nie ma (bez wycieku istnienia loginu), błąd zawsze 401 z tym samym komunikatem.
  Seed nie zawiera haseł.
- E-mail (`MailNotifier`): wysyłka po commicie transakcji (`@TransactionalEventListener`), błąd SMTP nie przerywa procesu;
  audyt `EMAIL_SENT` z liczbą odbiorców (bez adresów). Domyślnie wyłączony (`MAIL_ENABLED=false`), w compose — Mailpit.
- Ankieta dobrostanu: dobrowolna, technicznie anonimowa (bez `user_id`, czas z dokładnością do dnia), bez audytu
  i bez powiązania z cyklem; widoczna wyłącznie zbiorczo.
- Porty hosta: tylko frontend (3000) i backend-core (8080); Postgres na 127.0.0.1, Gotenberg i ai-gateway wyłącznie w sieci
  Dockera. W produkcji backend-core za reverse proxy z TLS (HSTS, CSP) i `DEMO_HEADER_ENABLED=false`.
- Rate-limiting (`RateLimitFilter`): logowanie per adres klienta (`X-Forwarded-For` tylko przy
  `app.rate-limit.trust-forwarded-for=true`, czyli gdy backend stoi za proxy), AI per użytkownik; stan w pamięci procesu.
- Limity rozmiaru AI: 64 KB w backend-core (`AiProxyService`) i w ai-gateway (`BodySizeLimit`); `maskedPrompt` w audycie
  przycinany do `app.ai.audit-prompt-max-chars`.
- Masker PII ma koszt liniowy — nowe wyrażenia regularne nie mogą wracać po długich ciągach (test DoS w `test_security`).
- Listy z wieloma osobami pobierają imiona jednym zapytaniem (`UserNames.byId`) — bez `findById` w pętli (N+1).

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
- Produkcja: tylko modele na licencji Apache 2.0 (PLLuM-12B / Bielik), bez wariantów `-nc`.

### Frontend
- Przeglądarka rozmawia wyłącznie z Next.js (proxy przez rewrites) — brak CORS, adresy usług tylko w build args.
- Wszystkie wywołania przez `lib/api.ts`: wyłącznie `Authorization: Bearer <JWT>` z `POST /api/auth/gov-login` lub `/local-login` (token w
  `localStorage`); frontend nie wysyła `X-Demo-User-Id`. 401 → wyczyszczenie sesji i zdarzenie `meritum:auth-expired`
  (UserProvider otwiera okno logowania „Sesja wygasła”); „Wyloguj” blokuje automatyczne logowanie do ponownego wyboru
  konta. Błędy RFC 7807 -> `ApiError`.
- Pierwsze uruchomienie: automatyczny gov-login jako Kierownik (bez ściany logowania); zmiana roli = nowy gov-login.
- Wywołania AI tylko przez `coreAi(operation, cycleId)` → `/api/core-ai/*` (audyt AI Act art. 26).
- Typy w `lib/types.ts` muszą odpowiadać `Dtos.java` / modelom Pydantic — zmiana DTO = zmiana typów. Żadnych lokalnych
  kopii kontraktów w komponentach.
- Cykl (`/cycles/[id]`) ma 6 zakładek: 1. Kryteria (opisy zachowań z zał. nr 1, data rozmowy, AI `suggest-criteria`,
  „Drukuj Część I”), 2. Rozmowy i IPRZ (ankieta przed rozmową z „Wyślij przełożonemu”, `interview-prep` § 5 ust. 1 / § 10,
  `.ics`, katalog działań), 3. Notatki i HR Partner (kafelki
  `hr-facts` → `hr-partner`), 4. Ocena i Sokratejskie AI (data rozmowy, `delayReason` po terminie — bez blokady, blokada
  wniosku przy 9. stopniu, podpowiedzi IPRZ), 5. Termin (§ 8) i Zakres (§ 9) (`reschedule`, decyzja wniosku, pismo
  `noticeText`, `scope-changes`), 6. Zapoznanie, Podpis i PDF (`MOBYWATEL_QES` / `PAPER_HYBRID`, PDF/A, PDF cz. I, EZD JSON,
  termin decyzji DG, ponowny sprzeciw po REDO, odwołanie do sądu pracy / legal hold dla HR i DG).
- Pulpit: „+ Nowy cykl oceny” (HR / bezpośredni przełożony), status terminu, badge art. 89 ust. 2, „Rozpocznij kolejny cykl”.
- Styl gov.pl: kolory wyłącznie z palety `gov`/`navy` (bez osobnego koloru dla AI — przycisk AI to niebieski kontur).
- `/admin` — kreator schematów bez ręcznej edycji JSON-a: formularz wstępnie wypełniony z najnowszej wersji (kod, data
  wejścia w życie, okres w miesiącach, maks. 0–3 kryteriów dodatkowych, kryteria własne), tabela kryteriów (nazwa, opisy
  zachowań, checkboxy obowiązkowości [A] [B] [C], dodawanie/usuwanie nowych). Pozostałe klucze `config` przejmowane z
  wersji bazowej; walidacja wyłącznie w backendzie (`CriteriaCatalog.from`, 422). JSON tylko w zwiniętym podglądzie dla IT.
  Przy każdej wersji „Pobierz pakiet reguł (.json)”.
- `/hr` — karty „Alerty kadrowe” (`/api/hr/alerts`) i „Retencja i dane pomocnicze” (`purge-working-notes`); status
  zatrudnienia, opis stanowiska i data zatrudnienia w edycji pracownika. Kreator cyklu blokuje utworzenie, gdy
  `due-preview` zwraca `blockedReason`.
- `/admin` — karta „Konta lokalne — tryb Mały Urząd” (urzędy, włączanie logowania hasłem, konta, hasła, import CSV);
  okno logowania ma trzeci sposób „Konto lokalne urzędu” (`localLogin` w `lib/api.ts`).
- `/hr` — karta „Zaległości i drabina eskalacji” (`/api/hr/overdue`, badge szczebla, „Zmień oceniającego” → formularz
  handover z ustawionym pracownikiem) i „Pobierz raport sprawozdawczy (CSV)” (`/api/hr/reports/export`).
- `/audit` — `?limit=100`, filtr „Tylko interakcje AI” (`action` z prefiksem `AI_`), `ModeBadge` z `payload.mode`,
  `MaskedPrompt` z `payload.maskedPrompt`, surowy payload w `<details>`.
- Pobieranie plików (PDF, .ics, CSV, pakiet reguł) wyłącznie przez `download()` z `lib/api.ts` (nagłówek uwierzytelnienia).
- Uprawnienia w UI wynikają z relacji do cyklu (evaluatorId/employeeId), tak jak w backendzie; UI tylko ukrywa akcje, decyduje backend.
- Reguły prawa nie są duplikowane w UI: katalog kryteriów z `/api/schemas/{id}`, wynik oceny z silnika reguł (średnia w UI to tylko podgląd).
- Dostępność: WCAG 2.1 AA — etykiety pól, focus-visible, role tablist/tab, `aria-*`, kontrast gov-blue (#0052A5) na bieli.
- Motywy: kolory wyłącznie przez tokeny CSS z `globals.css` (`--bg`, `--surface`, `--text`, `--accent`, `--status-*`…);
  `tailwind.config.ts` mapuje na nie `white`, `slate`, `gov`, `navy`, `red`, `amber`, `green` — nowe widoki nie używają
  surowych hexów. Filtr daltonizmu tylko na `#app-content-wrapper` (nie na `<html>`/`<body>`). Statusy: `StatusBadge`
  (ikona + wzór obramowania + tekst); `Badge`/`Alert` przy `data-status-shapes="on"` dostają kształty z CSS.
- Język UI: wyłącznie polski (bez „check-in”, „draft”, „legal hold”…); rozmowa rozpoczynająca i oceniająca oznaczane
  „Obowiązkowe (ustawa o SC)”, bieżąca — „Zalecany standard ZZL” (`INTERVIEW_LEGAL_STATUS`). Formularze z danymi osobowymi
  mają `VisibilityBanner`, wyniki AI — `AiComplianceBadge` (`complianceMeta`).
- Statystyki: backend zwraca `{value, suppressed}` i `kAnonymity` — UI pokazuje `<k` dla `suppressed`, nie liczy progu;
  karta dobrostanu pokazuje średnie tylko, gdy backend je zwrócił (≥ k odpowiedzi).

### Baza / migracje
- Migracje są niezmienne po wypchnięciu — zmiana = nowy plik `V{n}__opis.sql`.
- Seed demo tylko w migracjach z `seed` lub `demo_data` w nazwie.
- Testy integracyjne (`it/`) uruchamiają wszystkie migracje na PostgreSQL w Testcontainers — wymagany działający Docker
  (bez Dockera są pomijane).

### Testy bezpieczeństwa
- 0 testów `xfail` — luki z audytu 21.11.2026 są naprawione i pilnowane regresjami `test_gap_*` (`ai-gateway`) oraz
  `SecurityIntegrationTest` / `RateLimitFilterTest` (`backend-core`). Nowa luka = najpierw test, potem poprawka.
- `make loadtest` nie zmienia stanu cykli, ale tworzy wpisy `LOGIN` / `AI_*` w `audit_logs`.

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
make loadtest  # obciążenie + ataki na działający stos (po teście: make reset)
```
