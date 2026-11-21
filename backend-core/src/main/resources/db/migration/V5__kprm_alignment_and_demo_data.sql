-- Etap 5: dostosowanie do oficjalnych wzorów KPRM (cz. I–III), rejestry procesu i rozbudowany seed demo.
-- Dane demo spójne z regułami § 11 (średnie, poziomy) i zasadami liczenia terminów KPRM.

UPDATE schema_versions SET config_json = $json$
{
  "legalBasis": "Rozporządzenie Prezesa Rady Ministrów z dnia 4 kwietnia 2016 r. w sprawie warunków i sposobu przeprowadzania ocen okresowych urzędników służby cywilnej i pracowników służby cywilnej (Dz. U. poz. 470)",
  "active": true,
  "scale": [
    {
      "value": 1,
      "label": "znacznie poniżej oczekiwań"
    },
    {
      "value": 3,
      "label": "poniżej oczekiwań"
    },
    {
      "value": 5,
      "label": "na poziomie oczekiwań"
    },
    {
      "value": 7,
      "label": "powyżej oczekiwań"
    },
    {
      "value": 9,
      "label": "znacznie powyżej oczekiwań"
    }
  ],
  "sheetTypes": {
    "A": "Kierownik urzędu (§ 3 ust. 2)",
    "B": "Stanowisko polegające na bezpośrednim kierowaniu pracownikami (§ 3 ust. 3)",
    "C": "Pozostałe stanowiska (§ 3 ust. 4)"
  },
  "criteriaRules": {
    "maxAdditional": 3,
    "allowCustom": false
  },
  "criteria": [
    {
      "code": "K01",
      "name": "Komunikacja",
      "description": [
        "Koncentrowanie się na wypowiedzi rozmówcy i dbanie o wzajemne zrozumienie.",
        "Wypowiadanie się w sposób jasny i precyzyjny, dostosowany do sytuacji i rozmówcy (dobieranie stylu, języka i treści).",
        "Formułowanie jasnych i precyzyjnych wypowiedzi pisemnych dostosowanych do sytuacji i odbiorcy (dobieranie stylu, języka i treści).",
        "Przedstawianie przekonujących argumentów odpowiednio do sytuacji."
      ]
    },
    {
      "code": "K02",
      "name": "Kreatywność",
      "description": [
        "Proponowanie lub tworzenie nowych rozwiązań.",
        "Nieschematyczne podejście do zadań."
      ]
    },
    {
      "code": "K03",
      "name": "Negocjowanie",
      "description": [
        "Przygotowywanie się do negocjacji, w szczególności opracowanie projektu stanowiska, zakresu możliwych ustępstw i argumentacji.",
        "Stosowanie technik i strategii negocjacyjnych.",
        "Osiągnięcie efektu negocjacji akceptowanego przez przełożonych."
      ]
    },
    {
      "code": "K04",
      "name": "Organizacja pracy i orientacja na osiąganie celów",
      "description": [
        "Określanie sposobu realizacji zadań/celów.",
        "Realizowanie zadań/celów w kolejności uwzględniającej ich wagę i pilność (ustalanie priorytetów działania).",
        "Dobieranie niezbędnych zasobów umożliwiających realizację zadań/celów.",
        "Dostosowywanie się do zmieniających się okoliczności.",
        "Wytrwałość i zaangażowanie w realizację zadań/celów."
      ]
    },
    {
      "code": "K05",
      "name": "Orientacja na klienta/interesanta",
      "description": [
        "Taktowne zachowanie, wyrażające szacunek i zrozumienie dla drugiej osoby.",
        "Reagowanie na potrzeby klienta/interesanta, w szczególności rozpoznawanie jego potrzeb i proponowanie rozwiązań."
      ]
    },
    {
      "code": "K06",
      "name": "Podejmowanie decyzji i odpowiedzialność",
      "description": [
        "Wypracowywanie alternatywnych rozwiązań na podstawie analizy informacji, w szczególności potencjalnego ryzyka.",
        "Podejmowanie decyzji (decyzyjność).",
        "Przyjmowanie odpowiedzialności za efekty podjętych decyzji."
      ]
    },
    {
      "code": "K07",
      "name": "Radzenie sobie w sytuacjach kryzysowych",
      "description": [
        "Rozpoznawanie sytuacji kryzysowych i informowanie o nich zainteresowanych.",
        "Podejmowanie działań mających na celu rozwiązanie kryzysu.",
        "Wyciąganie wniosków z sytuacji kryzysowych."
      ]
    },
    {
      "code": "K08",
      "name": "Rzetelność",
      "description": [
        "Wykonywanie zadań zgodnie z obowiązującymi standardami (przepisami, zasadami, procedurami) oraz dbałość o jakość rezultatów.",
        "Dotrzymywanie zobowiązań (realizacja zadań) w wyznaczonym czasie."
      ]
    },
    {
      "code": "K09",
      "name": "Myślenie analityczne",
      "description": [
        "Rozróżnianie informacji istotnych i nieistotnych.",
        "Dostrzeganie zależności między informacjami.",
        "Formułowanie wniosków."
      ]
    },
    {
      "code": "K10",
      "name": "Wykorzystywanie wiedzy i doskonalenie zawodowe",
      "description": [
        "Wykorzystywanie wiedzy przy realizacji zadań na zajmowanym stanowisku pracy.",
        "Doskonalenie wiedzy i umiejętności niezbędnych do wykonywania zadań na zajmowanym stanowisku pracy, w szczególności wyszukiwanie i pozyskiwanie wiedzy, korzystanie z dobrych praktyk i doświadczeń innych.",
        "Korzystanie z dostępnych źródeł informacji, rozwiązań i technologii."
      ]
    },
    {
      "code": "K11",
      "name": "Współpraca",
      "description": [
        "Włączanie się w działania realizowane razem z innymi osobami, jeśli istnieje taka możliwość.",
        "Dbanie o przepływ informacji.",
        "Dbanie o dobrą atmosferę i otwartość we współpracy.",
        "Dostrzeganie wkładu pracy innych osób.",
        "Branie pod uwagę zdania innych osób.",
        "Dzielenie się wiedzą i doświadczeniem."
      ]
    },
    {
      "code": "K12",
      "name": "Wystąpienia publiczne",
      "description": [
        "Wykorzystywanie różnych środków przekazu (np. narzędzi multimedialnych) i dbałość o sposób prezentacji (w tym autoprezentacji).",
        "Nawiązywanie i utrzymywanie kontaktu z audytorium.",
        "Zarządzanie czasem wystąpienia, w szczególności przestrzeganie ram czasowych, elastyczne reagowanie na potrzeby audytorium.",
        "Radzenie sobie ze stresem i emocjami, w szczególności wywołanymi krytyką i trudnymi pytaniami."
      ]
    },
    {
      "code": "K13",
      "name": "Zarządzanie ludźmi",
      "description": [
        "Przydzielanie zakresu odpowiedzialności, zadań i uprawnień pracownikom (delegowanie zadań).",
        "Monitorowanie realizacji zadań oraz w razie potrzeby podejmowanie działań korygujących lub zapobiegawczych.",
        "Motywowanie pracowników.",
        "Udzielanie pracownikom informacji zwrotnej opartej na ocenie ich zaangażowania i sposobu realizacji zadań.",
        "Rozwiązywanie konfliktów.",
        "Wspieranie pracowników w rozwoju zawodowym, w szczególności identyfikowanie potrzeb rozwojowych, proponowanie działań rozwojowych oraz monitorowanie ich realizacji."
      ]
    },
    {
      "code": "K14",
      "name": "Zarządzanie strategiczne",
      "description": [
        "Tworzenie wizji oraz wyznaczanie celów strategicznych w oparciu o całościowe i perspektywiczne widzenie organizacji, otoczenia oraz strategie rozwoju.",
        "Formułowanie celów operacyjnych na podstawie celów strategicznych.",
        "Analiza ryzyka planowanych działań o charakterze strategicznym."
      ]
    }
  ],
  "mandatoryBySheet": {
    "A": [
      "K13",
      "K06",
      "K04",
      "K11",
      "K01",
      "K14"
    ],
    "B": [
      "K13",
      "K06",
      "K04",
      "K11",
      "K01"
    ],
    "C": [
      "K10",
      "K08",
      "K04",
      "K11",
      "K01"
    ]
  }
}
$json$::jsonb WHERE version_code = 'v1';

ALTER TABLE offices ADD COLUMN city VARCHAR(100);

ALTER TABLE users
    ADD COLUMN first_name          VARCHAR(100),
    ADD COLUMN last_name           VARCHAR(150),
    ADD COLUMN department          VARCHAR(255),
    ADD COLUMN position_title      VARCHAR(255),
    ADD COLUMN appointment_year    INT CHECK (appointment_year BETWEEN 1990 AND 2100),
    ADD COLUMN civil_service_grade INT CHECK (civil_service_grade BETWEEN 1 AND 9),
    ADD COLUMN last_grade_date     DATE;

ALTER TABLE evaluation_cycles
    ADD COLUMN criteria_interview_date   DATE,
    ADD COLUMN criteria_confirmed_on     DATE,
    ADD COLUMN evaluation_interview_date DATE,
    ADD COLUMN evaluated_on              DATE,
    ADD COLUMN place                     VARCHAR(100),
    ADD COLUMN appeal_decision_deadline  DATE;

-- Historia ocen (system + oceny historyczne sprzed wdrożenia) — podstawa danych „poprzedniej oceny” i art. 89 ust. 2.
CREATE TABLE evaluation_history (
    id           BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    user_id      BIGINT      NOT NULL REFERENCES users (id),
    office_id    BIGINT      NOT NULL REFERENCES offices (id),
    cycle_id     BIGINT UNIQUE REFERENCES evaluation_cycles (id) ON DELETE SET NULL,
    outcome      VARCHAR(16) NOT NULL CHECK (outcome IN ('POSITIVE', 'NEGATIVE')),
    level        VARCHAR(32) NOT NULL CHECK (level IN ('SIGNIFICANTLY_BELOW', 'BELOW', 'MEETS', 'ABOVE', 'SIGNIFICANTLY_ABOVE')),
    evaluated_on DATE        NOT NULL,
    source       VARCHAR(16) NOT NULL CHECK (source IN ('SYSTEM', 'HISTORICAL')),
    entered_by   BIGINT REFERENCES users (id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_history_user ON evaluation_history (user_id, evaluated_on);

-- Dobrowolna ankieta dobrostanu: odseparowana od oceny (brak powiązania z cyklem), raportowana wyłącznie zbiorczo (k-anonimowość).
CREATE TABLE wellbeing_surveys (
    id                 BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    user_id            BIGINT      NOT NULL REFERENCES users (id),
    office_id          BIGINT      NOT NULL REFERENCES offices (id),
    workload           INT         NOT NULL CHECK (workload BETWEEN 1 AND 5),
    energy             INT         NOT NULL CHECK (energy BETWEEN 1 AND 5),
    support            INT         NOT NULL CHECK (support BETWEEN 1 AND 5),
    wants_conversation BOOLEAN     NOT NULL DEFAULT FALSE,
    submitted_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_wellbeing_office ON wellbeing_surveys (office_id, submitted_at);

-- Rejestr zmian zakresu obowiązków (§ 9 ust. 1, art. 81 ust. 6).
CREATE TABLE scope_changes (
    id                 BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    cycle_id           BIGINT       NOT NULL REFERENCES evaluation_cycles (id) ON DELETE CASCADE,
    user_id            BIGINT       NOT NULL REFERENCES users (id),
    change_date        DATE         NOT NULL,
    new_position_title VARCHAR(255),
    description        TEXT         NOT NULL,
    significant        BOOLEAN      NOT NULL,
    outcome            VARCHAR(32)  NOT NULL CHECK (outcome IN ('NONE', 'CRITERIA_RESET', 'EVALUATION_REQUIRED')),
    action_deadline    DATE,
    recorded_by        BIGINT       NOT NULL REFERENCES users (id),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Zmiany terminu oceny (art. 81 ust. 8, § 8) wraz z wnioskami ocenianego.
CREATE TABLE reschedule_requests (
    id                BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    cycle_id          BIGINT      NOT NULL REFERENCES evaluation_cycles (id) ON DELETE CASCADE,
    mode              VARCHAR(32) NOT NULL CHECK (mode IN ('EVALUATOR_CHANGE', 'EMPLOYEE_ABSENCE', 'EVALUATOR_ABSENCE', 'EMPLOYEE_REQUEST')),
    status            VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'APPLIED', 'REJECTED')),
    reason            TEXT        NOT NULL,
    previous_due_date DATE        NOT NULL,
    proposed_due_date DATE        NOT NULL,
    new_due_date      DATE,
    notice_text       TEXT,
    requested_by      BIGINT      NOT NULL REFERENCES users (id),
    decided_by        BIGINT REFERENCES users (id),
    decision_comment  TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_at        TIMESTAMPTZ
);
CREATE INDEX idx_reschedule_cycle ON reschedule_requests (cycle_id);

UPDATE offices SET city = 'Warszawa' WHERE id = 1;

UPDATE users SET first_name = 'Anna', last_name = 'Kowalska', department = 'Biuro Dyrektora Generalnego', position_title = 'Dyrektor generalny', appointment_year = 2012, civil_service_grade = 6, last_grade_date = DATE '2022-03-01' WHERE id = 1;
UPDATE users SET first_name = 'Piotr', last_name = 'Nowak', department = 'Departament Cyfryzacji – Wydział Rozwoju Systemów', position_title = 'Naczelnik wydziału', appointment_year = 2015, civil_service_grade = 4, last_grade_date = DATE '2023-05-15' WHERE id = 2;
UPDATE users SET first_name = 'Katarzyna', last_name = 'Wiśniewska', department = 'Departament Cyfryzacji – Wydział Rozwoju Systemów', position_title = 'Główny specjalista', appointment_year = 2019, civil_service_grade = 2, last_grade_date = DATE '2023-09-01' WHERE id = 3;
UPDATE users SET first_name = 'Marek', last_name = 'Zieliński', department = 'Biuro Kadr i Szkoleń', position_title = 'Starszy specjalista', appointment_year = 2017, civil_service_grade = 3, last_grade_date = DATE '2022-11-01' WHERE id = 4;
UPDATE users SET first_name = 'Ewa', last_name = 'Lewandowska', department = 'Departament Służby Cywilnej KPRM', position_title = 'Administrator systemu', appointment_year = NULL, civil_service_grade = NULL, last_grade_date = NULL WHERE id = 5;
INSERT INTO users (id, office_id, full_name, email, role, is_civil_servant, supervisor_id, first_name, last_name, department, position_title, appointment_year, civil_service_grade, last_grade_date)
VALUES (6, 1, 'Tomasz Wójcik', 'tomasz.wojcik@mc-demo.gov.pl', 'EVALUATOR', TRUE, 1, 'Tomasz', 'Wójcik', 'Departament Cyfryzacji – Wydział Analiz i Danych', 'Naczelnik wydziału', 2014, 5, DATE '2022-06-01');
INSERT INTO users (id, office_id, full_name, email, role, is_civil_servant, supervisor_id, first_name, last_name, department, position_title, appointment_year, civil_service_grade, last_grade_date)
VALUES (7, 1, 'Agnieszka Kamińska', 'agnieszka.kaminska@mc-demo.gov.pl', 'EVALUATED', TRUE, 2, 'Agnieszka', 'Kamińska', 'Departament Cyfryzacji – Wydział Rozwoju Systemów', 'Starszy specjalista', 2016, 3, DATE '2021-05-10');
INSERT INTO users (id, office_id, full_name, email, role, is_civil_servant, supervisor_id, first_name, last_name, department, position_title, appointment_year, civil_service_grade, last_grade_date)
VALUES (8, 1, 'Michał Lewicki', 'michal.lewicki@mc-demo.gov.pl', 'EVALUATED', FALSE, 2, 'Michał', 'Lewicki', 'Departament Cyfryzacji – Wydział Rozwoju Systemów', 'Specjalista', NULL, NULL, NULL);
INSERT INTO users (id, office_id, full_name, email, role, is_civil_servant, supervisor_id, first_name, last_name, department, position_title, appointment_year, civil_service_grade, last_grade_date)
VALUES (9, 1, 'Joanna Dąbrowska', 'joanna.dabrowska@mc-demo.gov.pl', 'EVALUATED', TRUE, 6, 'Joanna', 'Dąbrowska', 'Departament Cyfryzacji – Wydział Analiz i Danych', 'Główny specjalista', 2018, 2, DATE '2022-02-01');
INSERT INTO users (id, office_id, full_name, email, role, is_civil_servant, supervisor_id, first_name, last_name, department, position_title, appointment_year, civil_service_grade, last_grade_date)
VALUES (10, 1, 'Paweł Kozłowski', 'pawel.kozlowski@mc-demo.gov.pl', 'EVALUATED', FALSE, 6, 'Paweł', 'Kozłowski', 'Departament Cyfryzacji – Wydział Analiz i Danych', 'Specjalista', NULL, NULL, NULL);
INSERT INTO users (id, office_id, full_name, email, role, is_civil_servant, supervisor_id, first_name, last_name, department, position_title, appointment_year, civil_service_grade, last_grade_date)
VALUES (11, 1, 'Magdalena Jankowska', 'magdalena.jankowska@mc-demo.gov.pl', 'EVALUATED', TRUE, 6, 'Magdalena', 'Jankowska', 'Departament Cyfryzacji – Wydział Analiz i Danych', 'Starszy specjalista', 2020, 1, DATE '2020-07-01');
INSERT INTO users (id, office_id, full_name, email, role, is_civil_servant, supervisor_id, first_name, last_name, department, position_title, appointment_year, civil_service_grade, last_grade_date)
VALUES (12, 1, 'Krzysztof Mazur', 'krzysztof.mazur@mc-demo.gov.pl', 'EVALUATED', FALSE, 2, 'Krzysztof', 'Mazur', 'Departament Cyfryzacji – Wydział Rozwoju Systemów', 'Referent', NULL, NULL, NULL);
INSERT INTO users (id, office_id, full_name, email, role, is_civil_servant, supervisor_id, first_name, last_name, department, position_title, appointment_year, civil_service_grade, last_grade_date)
VALUES (13, 1, 'Aleksandra Krawczyk', 'aleksandra.krawczyk@mc-demo.gov.pl', 'EVALUATED', FALSE, 2, 'Aleksandra', 'Krawczyk', 'Departament Cyfryzacji – Wydział Rozwoju Systemów', 'Starszy referent', NULL, NULL, NULL);
INSERT INTO users (id, office_id, full_name, email, role, is_civil_servant, supervisor_id, first_name, last_name, department, position_title, appointment_year, civil_service_grade, last_grade_date)
VALUES (14, 1, 'Bartosz Piotrowski', 'bartosz.piotrowski@mc-demo.gov.pl', 'EVALUATED', TRUE, 6, 'Bartosz', 'Piotrowski', 'Departament Cyfryzacji – Wydział Analiz i Danych', 'Główny specjalista', 2013, 9, DATE '2021-01-04');
INSERT INTO users (id, office_id, full_name, email, role, is_civil_servant, supervisor_id, first_name, last_name, department, position_title, appointment_year, civil_service_grade, last_grade_date)
VALUES (15, 1, 'Natalia Grabowska', 'natalia.grabowska@mc-demo.gov.pl', 'EVALUATED', FALSE, 6, 'Natalia', 'Grabowska', 'Departament Cyfryzacji – Wydział Analiz i Danych', 'Specjalista', NULL, NULL, NULL);
INSERT INTO users (id, office_id, full_name, email, role, is_civil_servant, supervisor_id, first_name, last_name, department, position_title, appointment_year, civil_service_grade, last_grade_date)
VALUES (16, 1, 'Rafał Zając', 'rafal.zajac@mc-demo.gov.pl', 'EVALUATED', FALSE, 2, 'Rafał', 'Zając', 'Departament Cyfryzacji – Wydział Rozwoju Systemów', 'Starszy specjalista', NULL, NULL, NULL);

UPDATE evaluation_cycles SET criteria_interview_date = DATE '2025-01-10', criteria_confirmed_on = DATE '2025-01-13', place = 'Warszawa' WHERE id = 1;
UPDATE evaluation_cycles SET place = 'Warszawa' WHERE id = 2;

INSERT INTO evaluation_cycles (id, office_id, employee_id, evaluator_id, schema_version_id, sheet_type, status, start_date, due_date, criteria_json, scores_json, average_score, performance_level, final_result, result_explanation, justification, iprz_notes, grade_request_json, acknowledged_at, appeal_deadline, criteria_interview_date, criteria_confirmed_on, evaluation_interview_date, evaluated_on, place)
VALUES (3, 1, 7, 2, 1, 'C', 'COMPLETED', DATE '2024-06-15', DATE '2026-06-15', '[{"code": "K10", "name": "Wykorzystywanie wiedzy i doskonalenie zawodowe", "mandatory": true}, {"code": "K08", "name": "Rzetelność", "mandatory": true}, {"code": "K04", "name": "Organizacja pracy i orientacja na osiąganie celów", "mandatory": true}, {"code": "K11", "name": "Współpraca", "mandatory": true}, {"code": "K01", "name": "Komunikacja", "mandatory": true}]'::jsonb, '{"K10": 7, "K08": 7, "K04": 7, "K11": 7, "K01": 7}'::jsonb, 7.0, 'ABOVE', 'POSITIVE', 'Średnia 7.0 (powyżej oczekiwań) -> ocena pozytywna', 'Konsekwentnie wysoka jakość analiz i inicjatywa w usprawnianiu procesów wydziału.', 'Udział w szkoleniu specjalistycznym z obszaru zadań wydziału oraz mentoring wewnętrzny; przegląd postępów po 6 miesiącach.', NULL, DATE '2026-06-15', DATE '2026-06-22', DATE '2024-06-22', DATE '2024-06-23', DATE '2026-06-09', DATE '2026-06-12', 'Warszawa');
INSERT INTO evaluation_cycles (id, office_id, employee_id, evaluator_id, schema_version_id, sheet_type, status, start_date, due_date, criteria_json, scores_json, average_score, performance_level, final_result, result_explanation, justification, iprz_notes, grade_request_json, acknowledged_at, appeal_deadline, criteria_interview_date, criteria_confirmed_on, evaluation_interview_date, evaluated_on, place)
VALUES (4, 1, 8, 2, 1, 'C', 'COMPLETED', DATE '2024-03-13', DATE '2026-03-13', '[{"code": "K10", "name": "Wykorzystywanie wiedzy i doskonalenie zawodowe", "mandatory": true}, {"code": "K08", "name": "Rzetelność", "mandatory": true}, {"code": "K04", "name": "Organizacja pracy i orientacja na osiąganie celów", "mandatory": true}, {"code": "K11", "name": "Współpraca", "mandatory": true}, {"code": "K01", "name": "Komunikacja", "mandatory": true}]'::jsonb, '{"K10": 5, "K08": 5, "K04": 7, "K11": 5, "K01": 5}'::jsonb, 5.4, 'MEETS', 'POSITIVE', 'Średnia 5.4 (na poziomie oczekiwań) -> ocena pozytywna', 'Zadania w okresie oceny były realizowane terminowo i zgodnie z obowiązującymi procedurami; współpraca w zespole przebiegała bez zastrzeżeń.', 'Udział w szkoleniu specjalistycznym z obszaru zadań wydziału oraz mentoring wewnętrzny; przegląd postępów po 6 miesiącach.', NULL, DATE '2026-03-11', DATE '2026-03-18', DATE '2024-03-20', DATE '2024-03-21', DATE '2026-03-07', DATE '2026-03-10', 'Warszawa');
INSERT INTO evaluation_cycles (id, office_id, employee_id, evaluator_id, schema_version_id, sheet_type, status, start_date, due_date, criteria_json, scores_json, average_score, performance_level, final_result, result_explanation, justification, iprz_notes, grade_request_json, acknowledged_at, appeal_deadline, criteria_interview_date, criteria_confirmed_on, evaluation_interview_date, evaluated_on, place)
VALUES (5, 1, 9, 6, 1, 'C', 'COMPLETED', DATE '2024-04-23', DATE '2026-04-23', '[{"code": "K10", "name": "Wykorzystywanie wiedzy i doskonalenie zawodowe", "mandatory": true}, {"code": "K08", "name": "Rzetelność", "mandatory": true}, {"code": "K04", "name": "Organizacja pracy i orientacja na osiąganie celów", "mandatory": true}, {"code": "K11", "name": "Współpraca", "mandatory": true}, {"code": "K01", "name": "Komunikacja", "mandatory": true}, {"code": "K09", "name": "Myślenie analityczne", "mandatory": false}]'::jsonb, '{"K10": 5, "K08": 5, "K04": 5, "K11": 5, "K01": 5, "K09": 5}'::jsonb, 5.0, 'MEETS', 'POSITIVE', 'Średnia 5.0 (na poziomie oczekiwań) -> ocena pozytywna', 'Zadania w okresie oceny były realizowane terminowo i zgodnie z obowiązującymi procedurami; współpraca w zespole przebiegała bez zastrzeżeń.', 'Udział w szkoleniu specjalistycznym z obszaru zadań wydziału oraz mentoring wewnętrzny; przegląd postępów po 6 miesiącach.', NULL, DATE '2026-04-21', DATE '2026-04-28', DATE '2024-04-30', DATE '2024-05-01', DATE '2026-04-17', DATE '2026-04-20', 'Warszawa');
INSERT INTO evaluation_cycles (id, office_id, employee_id, evaluator_id, schema_version_id, sheet_type, status, start_date, due_date, criteria_json, scores_json, average_score, performance_level, final_result, result_explanation, justification, iprz_notes, grade_request_json, acknowledged_at, appeal_deadline, criteria_interview_date, criteria_confirmed_on, evaluation_interview_date, evaluated_on, place)
VALUES (6, 1, 10, 6, 1, 'C', 'COMPLETED', DATE '2024-08-06', DATE '2026-08-06', '[{"code": "K10", "name": "Wykorzystywanie wiedzy i doskonalenie zawodowe", "mandatory": true}, {"code": "K08", "name": "Rzetelność", "mandatory": true}, {"code": "K04", "name": "Organizacja pracy i orientacja na osiąganie celów", "mandatory": true}, {"code": "K11", "name": "Współpraca", "mandatory": true}, {"code": "K01", "name": "Komunikacja", "mandatory": true}]'::jsonb, '{"K10": 3, "K08": 5, "K04": 3, "K11": 3, "K01": 5}'::jsonb, 3.8, 'BELOW', 'NEGATIVE', 'Średnia 3.8 (poniżej oczekiwań) -> ocena negatywna', 'W okresie oceny wielokrotnie konieczne były korekty przygotowywanych materiałów, a część zadań była realizowana po wyznaczonych terminach mimo wcześniejszych rozmów korygujących. Wymagania kryteriów rzetelności oraz organizacji pracy nie zostały spełnione.', 'Szkolenie z kontroli jakości dokumentów oraz wdrożenie listy kontrolnej przed przekazaniem materiałów do dalszego obiegu.', NULL, DATE '2026-08-04', DATE '2026-08-11', DATE '2024-08-13', DATE '2024-08-14', DATE '2026-07-31', DATE '2026-08-03', 'Warszawa');
INSERT INTO evaluation_cycles (id, office_id, employee_id, evaluator_id, schema_version_id, sheet_type, status, start_date, due_date, criteria_json, scores_json, average_score, performance_level, final_result, result_explanation, justification, iprz_notes, grade_request_json, acknowledged_at, appeal_deadline, criteria_interview_date, criteria_confirmed_on, evaluation_interview_date, evaluated_on, place)
VALUES (7, 1, 11, 6, 1, 'C', 'COMPLETED', DATE '2024-07-04', DATE '2026-07-06', '[{"code": "K10", "name": "Wykorzystywanie wiedzy i doskonalenie zawodowe", "mandatory": true}, {"code": "K08", "name": "Rzetelność", "mandatory": true}, {"code": "K04", "name": "Organizacja pracy i orientacja na osiąganie celów", "mandatory": true}, {"code": "K11", "name": "Współpraca", "mandatory": true}, {"code": "K01", "name": "Komunikacja", "mandatory": true}]'::jsonb, '{"K10": 7, "K08": 7, "K04": 5, "K11": 7, "K01": 7}'::jsonb, 6.6, 'ABOVE', 'POSITIVE', 'Średnia 6.6 (powyżej oczekiwań) -> ocena pozytywna', 'Wyróżniające wyniki w projekcie migracji danych i dzielenie się wiedzą z zespołem.', 'Udział w szkoleniu specjalistycznym z obszaru zadań wydziału oraz mentoring wewnętrzny; przegląd postępów po 6 miesiącach.', '{"requested": true, "motivation": "Wyróżniające wykonywanie obowiązków, samodzielne prowadzenie projektu migracji danych oraz aktywne wspieranie wdrożenia nowych członków zespołu uzasadniają przyznanie kolejnego stopnia służbowego."}'::jsonb, DATE '2026-07-02', DATE '2026-07-09', DATE '2024-07-11', DATE '2024-07-12', DATE '2026-06-28', DATE '2026-07-01', 'Warszawa');
INSERT INTO evaluation_cycles (id, office_id, employee_id, evaluator_id, schema_version_id, sheet_type, status, start_date, due_date, criteria_json, scores_json, average_score, performance_level, final_result, result_explanation, justification, iprz_notes, grade_request_json, acknowledged_at, appeal_deadline, criteria_interview_date, criteria_confirmed_on, evaluation_interview_date, evaluated_on, place)
VALUES (8, 1, 12, 2, 1, 'C', 'COMPLETED', DATE '2024-02-19', DATE '2026-02-19', '[{"code": "K10", "name": "Wykorzystywanie wiedzy i doskonalenie zawodowe", "mandatory": true}, {"code": "K08", "name": "Rzetelność", "mandatory": true}, {"code": "K04", "name": "Organizacja pracy i orientacja na osiąganie celów", "mandatory": true}, {"code": "K11", "name": "Współpraca", "mandatory": true}, {"code": "K01", "name": "Komunikacja", "mandatory": true}]'::jsonb, '{"K10": 5, "K08": 7, "K04": 5, "K11": 7, "K01": 5}'::jsonb, 5.8, 'MEETS', 'POSITIVE', 'Średnia 5.8 (na poziomie oczekiwań) -> ocena pozytywna', 'Zadania w okresie oceny były realizowane terminowo i zgodnie z obowiązującymi procedurami; współpraca w zespole przebiegała bez zastrzeżeń.', 'Udział w szkoleniu specjalistycznym z obszaru zadań wydziału oraz mentoring wewnętrzny; przegląd postępów po 6 miesiącach.', NULL, DATE '2026-02-17', DATE '2026-02-24', DATE '2024-02-26', DATE '2024-02-27', DATE '2026-02-13', DATE '2026-02-16', 'Warszawa');
INSERT INTO evaluation_cycles (id, office_id, employee_id, evaluator_id, schema_version_id, sheet_type, status, start_date, due_date, criteria_json, scores_json, average_score, performance_level, final_result, result_explanation, justification, iprz_notes, grade_request_json, acknowledged_at, appeal_deadline, criteria_interview_date, criteria_confirmed_on, evaluation_interview_date, evaluated_on, place)
VALUES (9, 1, 13, 2, 1, 'C', 'COMPLETED', DATE '2024-08-23', DATE '2026-08-24', '[{"code": "K10", "name": "Wykorzystywanie wiedzy i doskonalenie zawodowe", "mandatory": true}, {"code": "K08", "name": "Rzetelność", "mandatory": true}, {"code": "K04", "name": "Organizacja pracy i orientacja na osiąganie celów", "mandatory": true}, {"code": "K11", "name": "Współpraca", "mandatory": true}, {"code": "K01", "name": "Komunikacja", "mandatory": true}]'::jsonb, '{"K10": 1, "K08": 5, "K04": 5, "K11": 7, "K01": 5}'::jsonb, 4.6, 'MEETS', 'NEGATIVE', 'Średnia 4.6 (na poziomie oczekiwań), ale kryterium Wykorzystywanie wiedzy i doskonalenie zawodowe (K10) = 1 pkt -> ocena negatywna', 'Mimo poprawnej realizacji większości zadań stwierdzono poważne uchybienia w zakresie rzetelności: dokumenty przekazywane do akceptacji zawierały istotne błędy merytoryczne, a terminy kluczowych zadań nie były dotrzymywane. Ocena cząstkowa na poziomie znacznie poniżej oczekiwań w tym kryterium skutkuje oceną negatywną.', 'Szkolenie z kontroli jakości dokumentów oraz wdrożenie listy kontrolnej przed przekazaniem materiałów do dalszego obiegu.', NULL, DATE '2026-08-21', DATE '2026-08-28', DATE '2024-08-30', DATE '2024-08-31', DATE '2026-08-17', DATE '2026-08-20', 'Warszawa');
INSERT INTO evaluation_cycles (id, office_id, employee_id, evaluator_id, schema_version_id, sheet_type, status, start_date, due_date, criteria_json, scores_json, average_score, performance_level, final_result, result_explanation, justification, iprz_notes, grade_request_json, acknowledged_at, appeal_deadline, criteria_interview_date, criteria_confirmed_on, evaluation_interview_date, evaluated_on, place)
VALUES (10, 1, 14, 6, 1, 'C', 'COMPLETED', DATE '2024-09-17', DATE '2026-09-17', '[{"code": "K10", "name": "Wykorzystywanie wiedzy i doskonalenie zawodowe", "mandatory": true}, {"code": "K08", "name": "Rzetelność", "mandatory": true}, {"code": "K04", "name": "Organizacja pracy i orientacja na osiąganie celów", "mandatory": true}, {"code": "K11", "name": "Współpraca", "mandatory": true}, {"code": "K01", "name": "Komunikacja", "mandatory": true}]'::jsonb, '{"K10": 5, "K08": 5, "K04": 5, "K11": 5, "K01": 5}'::jsonb, 5.0, 'MEETS', 'POSITIVE', 'Średnia 5.0 (na poziomie oczekiwań) -> ocena pozytywna', 'Zadania w okresie oceny były realizowane terminowo i zgodnie z obowiązującymi procedurami; współpraca w zespole przebiegała bez zastrzeżeń.', 'Udział w szkoleniu specjalistycznym z obszaru zadań wydziału oraz mentoring wewnętrzny; przegląd postępów po 6 miesiącach.', NULL, DATE '2026-09-15', DATE '2026-09-22', DATE '2024-09-24', DATE '2024-09-25', DATE '2026-09-11', DATE '2026-09-14', 'Warszawa');
INSERT INTO evaluation_cycles (id, office_id, employee_id, evaluator_id, schema_version_id, sheet_type, status, start_date, due_date, criteria_json, scores_json, average_score, performance_level, final_result, result_explanation, justification, iprz_notes, grade_request_json, acknowledged_at, appeal_deadline, criteria_interview_date, criteria_confirmed_on, evaluation_interview_date, evaluated_on, place)
VALUES (11, 1, 6, 1, 1, 'B', 'COMPLETED', DATE '2024-10-08', DATE '2026-10-08', '[{"code": "K13", "name": "Zarządzanie ludźmi", "mandatory": true}, {"code": "K06", "name": "Podejmowanie decyzji i odpowiedzialność", "mandatory": true}, {"code": "K04", "name": "Organizacja pracy i orientacja na osiąganie celów", "mandatory": true}, {"code": "K11", "name": "Współpraca", "mandatory": true}, {"code": "K01", "name": "Komunikacja", "mandatory": true}]'::jsonb, '{"K13": 9, "K06": 9, "K04": 7, "K11": 9, "K01": 7}'::jsonb, 8.2, 'SIGNIFICANTLY_ABOVE', 'POSITIVE', 'Średnia 8.2 (znacznie powyżej oczekiwań) -> ocena pozytywna', 'Bardzo skuteczne kierowanie wydziałem w okresie wdrożenia hurtowni danych; wysoka ocena zespołu w badaniu atmosfery pracy.', 'Udział w szkoleniu specjalistycznym z obszaru zadań wydziału oraz mentoring wewnętrzny; przegląd postępów po 6 miesiącach.', NULL, DATE '2026-10-06', DATE '2026-10-13', DATE '2024-10-15', DATE '2024-10-16', DATE '2026-10-02', DATE '2026-10-05', 'Warszawa');
INSERT INTO evaluation_cycles (id, office_id, employee_id, evaluator_id, schema_version_id, sheet_type, status, start_date, due_date, criteria_json, scores_json, average_score, performance_level, final_result, result_explanation, justification, iprz_notes, grade_request_json, acknowledged_at, appeal_deadline, criteria_interview_date, criteria_confirmed_on, evaluation_interview_date, evaluated_on, place)
VALUES (12, 1, 15, 6, 1, 'C', 'AWAITING_ACK', DATE '2024-11-22', DATE '2026-11-23', '[{"code": "K10", "name": "Wykorzystywanie wiedzy i doskonalenie zawodowe", "mandatory": true}, {"code": "K08", "name": "Rzetelność", "mandatory": true}, {"code": "K04", "name": "Organizacja pracy i orientacja na osiąganie celów", "mandatory": true}, {"code": "K11", "name": "Współpraca", "mandatory": true}, {"code": "K01", "name": "Komunikacja", "mandatory": true}]'::jsonb, '{"K10": 5, "K08": 5, "K04": 7, "K11": 5, "K01": 5}'::jsonb, 5.4, 'MEETS', 'POSITIVE', 'Średnia 5.4 (na poziomie oczekiwań) -> ocena pozytywna', 'Zadania w okresie oceny były realizowane terminowo i zgodnie z obowiązującymi procedurami; współpraca w zespole przebiegała bez zastrzeżeń.', 'Udział w szkoleniu specjalistycznym z obszaru zadań wydziału oraz mentoring wewnętrzny; przegląd postępów po 6 miesiącach.', NULL, NULL, NULL, DATE '2024-11-29', DATE '2024-11-30', DATE '2026-11-16', DATE '2026-11-19', 'Warszawa');
INSERT INTO evaluation_cycles (id, office_id, employee_id, evaluator_id, schema_version_id, sheet_type, status, start_date, due_date, criteria_json, scores_json, average_score, performance_level, final_result, result_explanation, justification, iprz_notes, grade_request_json, acknowledged_at, appeal_deadline, criteria_interview_date, criteria_confirmed_on, evaluation_interview_date, evaluated_on, place, appeal_text, appealed_at, appeal_decision_deadline)
VALUES (14, 1, 16, 2, 1, 'C', 'APPEALED', DATE '2024-11-13', DATE '2026-11-13', '[{"code": "K10", "name": "Wykorzystywanie wiedzy i doskonalenie zawodowe", "mandatory": true}, {"code": "K08", "name": "Rzetelność", "mandatory": true}, {"code": "K04", "name": "Organizacja pracy i orientacja na osiąganie celów", "mandatory": true}, {"code": "K11", "name": "Współpraca", "mandatory": true}, {"code": "K01", "name": "Komunikacja", "mandatory": true}]'::jsonb, '{"K10": 3, "K08": 3, "K04": 3, "K11": 5, "K01": 3}'::jsonb, 3.4, 'BELOW', 'NEGATIVE', 'Średnia 3.4 (poniżej oczekiwań) -> ocena negatywna', 'Realizacja zadań wymagała stałego nadzoru, a terminy były regularnie przekraczane. Poziom spełniania kryteriów rzetelności, organizacji pracy i współpracy był niższy niż ustalone oczekiwania.', 'Szkolenie z kontroli jakości dokumentów oraz wdrożenie listy kontrolnej przed przekazaniem materiałów do dalszego obiegu.', NULL, DATE '2026-11-12', DATE '2026-11-19', DATE '2024-11-20', DATE '2024-11-21', DATE '2026-11-07', DATE '2026-11-10', 'Warszawa', 'Nie zgadzam się z oceną kryterium współpracy — w okresie oceny realizowałem dodatkowe zadania zlecone przez innych naczelników, które nie zostały uwzględnione.', TIMESTAMPTZ '2026-11-17 10:15:00+01', DATE '2026-12-01');
INSERT INTO evaluation_cycles (id, office_id, employee_id, evaluator_id, schema_version_id, sheet_type, status, start_date, due_date,
                               is_reevaluation, criteria_json, criteria_interview_date, criteria_confirmed_on, place)
VALUES (13, 1, 10, 6, 1, 'C', 'OBSERVATION', DATE '2026-08-04', DATE '2027-02-04', TRUE, '[{"code": "K10", "name": "Wykorzystywanie wiedzy i doskonalenie zawodowe", "mandatory": true}, {"code": "K08", "name": "Rzetelność", "mandatory": true}, {"code": "K04", "name": "Organizacja pracy i orientacja na osiąganie celów", "mandatory": true}, {"code": "K11", "name": "Współpraca", "mandatory": true}, {"code": "K01", "name": "Komunikacja", "mandatory": true}]'::jsonb,
        DATE '2026-08-10', DATE '2026-08-11', 'Warszawa');
INSERT INTO evaluation_cycles (id, office_id, employee_id, evaluator_id, schema_version_id, sheet_type, status, start_date, due_date,
                               criteria_json, scores_json, criteria_interview_date, criteria_confirmed_on, place)
VALUES (15, 1, 4, 1, 1, 'C', 'EVALUATION_DRAFT', DATE '2024-12-02', DATE '2026-12-02', '[{"code": "K10", "name": "Wykorzystywanie wiedzy i doskonalenie zawodowe", "mandatory": true}, {"code": "K08", "name": "Rzetelność", "mandatory": true}, {"code": "K04", "name": "Organizacja pracy i orientacja na osiąganie celów", "mandatory": true}, {"code": "K11", "name": "Współpraca", "mandatory": true}, {"code": "K01", "name": "Komunikacja", "mandatory": true}]'::jsonb,
        '{"K10": 5, "K08": 7}'::jsonb, DATE '2024-12-09', DATE '2024-12-10', 'Warszawa');

INSERT INTO evaluation_history (user_id, office_id, cycle_id, outcome, level, evaluated_on, source, entered_by) VALUES (7, 1, 3, 'POSITIVE', 'ABOVE', DATE '2026-06-12', 'SYSTEM', 4);
INSERT INTO evaluation_history (user_id, office_id, cycle_id, outcome, level, evaluated_on, source, entered_by) VALUES (8, 1, 4, 'POSITIVE', 'MEETS', DATE '2026-03-10', 'SYSTEM', 4);
INSERT INTO evaluation_history (user_id, office_id, cycle_id, outcome, level, evaluated_on, source, entered_by) VALUES (9, 1, 5, 'POSITIVE', 'MEETS', DATE '2026-04-20', 'SYSTEM', 4);
INSERT INTO evaluation_history (user_id, office_id, cycle_id, outcome, level, evaluated_on, source, entered_by) VALUES (10, 1, 6, 'NEGATIVE', 'BELOW', DATE '2026-08-03', 'SYSTEM', 4);
INSERT INTO evaluation_history (user_id, office_id, cycle_id, outcome, level, evaluated_on, source, entered_by) VALUES (11, 1, 7, 'POSITIVE', 'ABOVE', DATE '2026-07-01', 'SYSTEM', 4);
INSERT INTO evaluation_history (user_id, office_id, cycle_id, outcome, level, evaluated_on, source, entered_by) VALUES (12, 1, 8, 'POSITIVE', 'MEETS', DATE '2026-02-16', 'SYSTEM', 4);
INSERT INTO evaluation_history (user_id, office_id, cycle_id, outcome, level, evaluated_on, source, entered_by) VALUES (13, 1, 9, 'NEGATIVE', 'MEETS', DATE '2026-08-20', 'SYSTEM', 4);
INSERT INTO evaluation_history (user_id, office_id, cycle_id, outcome, level, evaluated_on, source, entered_by) VALUES (14, 1, 10, 'POSITIVE', 'MEETS', DATE '2026-09-14', 'SYSTEM', 4);
INSERT INTO evaluation_history (user_id, office_id, cycle_id, outcome, level, evaluated_on, source, entered_by) VALUES (6, 1, 11, 'POSITIVE', 'SIGNIFICANTLY_ABOVE', DATE '2026-10-05', 'SYSTEM', 4);
INSERT INTO evaluation_history (user_id, office_id, cycle_id, outcome, level, evaluated_on, source, entered_by) VALUES (16, 1, 14, 'NEGATIVE', 'BELOW', DATE '2026-11-10', 'SYSTEM', 4);
INSERT INTO evaluation_history (user_id, office_id, cycle_id, outcome, level, evaluated_on, source, entered_by) VALUES (7, 1, NULL, 'POSITIVE', 'ABOVE', DATE '2024-06-14', 'HISTORICAL', 4);
INSERT INTO evaluation_history (user_id, office_id, cycle_id, outcome, level, evaluated_on, source, entered_by) VALUES (6, 1, NULL, 'POSITIVE', 'ABOVE', DATE '2024-10-01', 'HISTORICAL', 4);
INSERT INTO evaluation_history (user_id, office_id, cycle_id, outcome, level, evaluated_on, source, entered_by) VALUES (3, 1, NULL, 'POSITIVE', 'MEETS', DATE '2024-12-20', 'HISTORICAL', 4);
INSERT INTO evaluation_history (user_id, office_id, cycle_id, outcome, level, evaluated_on, source, entered_by) VALUES (14, 1, NULL, 'POSITIVE', 'ABOVE', DATE '2024-09-10', 'HISTORICAL', 4);

INSERT INTO employee_notes (cycle_id, author_id, content, is_shared_after_eval, created_at)
VALUES (13, 6, 'Rozmowa wspierająca po ocenie negatywnej: ustalono cotygodniowy przegląd postępów i listę kontrolną jakości.', TRUE, TIMESTAMPTZ '2026-08-11 10:00:00+02'),
       (13, 6, 'Wyraźna poprawa terminowości we wrześniu — wszystkie zadania zamknięte w terminie.', TRUE, TIMESTAMPTZ '2026-09-30 15:00:00+02'),
       (15, 1, 'Sprawne przygotowanie naboru na 6 stanowisk w II kwartale.', TRUE, TIMESTAMPTZ '2026-06-30 12:00:00+02');

-- Wniosek ocenianej o zmianę terminu (art. 81 ust. 8 pkt 4) — oczekuje na decyzję oceniającego.
INSERT INTO reschedule_requests (cycle_id, mode, status, reason, previous_due_date, proposed_due_date, requested_by, created_at)
VALUES (1, 'EMPLOYEE_REQUEST', 'PENDING', 'Planowany urlop szkoleniowy w grudniu 2026 r. — proszę o przesunięcie terminu oceny po powrocie.',
        DATE '2026-12-31', DATE '2027-02-15', 3, TIMESTAMPTZ '2026-11-18 09:30:00+01');

-- Ankiety dobrostanu (dane do agregatów; brak powiązania z cyklami ocen).
INSERT INTO wellbeing_surveys (user_id, office_id, workload, energy, support, wants_conversation, submitted_at)
VALUES (3, 1, 5, 2, 3, TRUE,  TIMESTAMPTZ '2026-11-03 08:00:00+01'),
       (7, 1, 3, 4, 4, FALSE, TIMESTAMPTZ '2026-11-04 08:00:00+01'),
       (8, 1, 4, 3, 3, FALSE, TIMESTAMPTZ '2026-11-04 09:00:00+01'),
       (9, 1, 4, 3, 4, FALSE, TIMESTAMPTZ '2026-11-05 08:00:00+01'),
       (10, 1, 4, 2, 2, TRUE, TIMESTAMPTZ '2026-11-05 10:00:00+01'),
       (11, 1, 3, 4, 5, FALSE, TIMESTAMPTZ '2026-11-06 08:00:00+01'),
       (12, 1, 2, 4, 4, FALSE, TIMESTAMPTZ '2026-11-06 11:00:00+01');

INSERT INTO audit_logs (office_id, actor_id, action, entity_type, entity_id, payload_json)
VALUES (1, 4, 'EMPLOYEES_IMPORTED', 'USER', NULL, '{"imported": 11, "skipped": 0, "errors": 0}'),
       (1, 16, 'APPEAL_FILED', 'EVALUATION_CYCLE', 14, '{"from": "COMPLETED", "to": "APPEALED"}'),
       (1, 3, 'RESCHEDULE_REQUESTED', 'EVALUATION_CYCLE', 1, '{"mode": "EMPLOYEE_REQUEST"}');

SELECT setval(pg_get_serial_sequence('users', 'id'), (SELECT MAX(id) FROM users));
SELECT setval(pg_get_serial_sequence('evaluation_cycles', 'id'), (SELECT MAX(id) FROM evaluation_cycles));
