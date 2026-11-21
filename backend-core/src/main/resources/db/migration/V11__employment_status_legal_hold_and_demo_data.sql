-- Etap 1 (konsultacja z mentorami KPRM + audyt prawny): status zatrudnienia w służbie cywilnej, odwołanie do sądu pracy
-- (art. 83 ust. 5) z blokadą retencji (legal hold).

-- NULL = rekord sprzed V11 / wstawiony bez statusu: status wyprowadzany z is_civil_servant (zgodność wsteczna).
ALTER TABLE users
    ADD COLUMN employment_status VARCHAR(32) CHECK (employment_status IN
        ('URZEDNIK_SC', 'PRACOWNIK_SC_NIEOKRESLONY', 'PRACOWNIK_SC_OKRESLONY', 'WYZSZE_STANOWISKO_SC'));

UPDATE users SET employment_status = CASE WHEN is_civil_servant THEN 'URZEDNIK_SC' ELSE 'PRACOWNIK_SC_NIEOKRESLONY' END;

ALTER TABLE evaluation_cycles DROP CONSTRAINT evaluation_cycles_status_check;
ALTER TABLE evaluation_cycles ADD CONSTRAINT evaluation_cycles_status_check CHECK (status IN
    ('DRAFT_CRITERIA', 'CRITERIA_CONFIRMED', 'OBSERVATION', 'EVALUATION_DRAFT', 'AWAITING_ACK', 'COMPLETED', 'APPEALED',
     'APPEALED_TO_COURT'));

ALTER TABLE evaluation_cycles
    ADD COLUMN legal_hold             BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN court_appeal_filed_on  DATE,
    ADD COLUMN court_case_reference   VARCHAR(100),
    -- liczba rozstrzygnięć sprzeciwu (art. 83 ust. 4: po REDO ponowny sprzeciw) — SEQUENCE zaproszenia .ics
    ADD COLUMN appeal_round           INT     NOT NULL DEFAULT 0;

-- Seed demo: dyrektor generalny zajmuje wyższe stanowisko SC (nie podlega ocenie okresowej).
UPDATE users SET employment_status = 'WYZSZE_STANOWISKO_SC', is_civil_servant = FALSE WHERE id = 1;

-- Seed demo: pracownik na umowie na czas określony (pierwsza ocena, art. 37–38) i urzędnik z dwiema kolejnymi ocenami
-- negatywnymi (krytyczny alert HR — art. 71 ust. 1 pkt 1).
INSERT INTO users (id, office_id, full_name, email, role, is_civil_servant, employment_status, supervisor_id, first_name,
                   last_name, department, position_title, appointment_year, civil_service_grade, last_grade_date)
VALUES (20, 1, 'Oliwia Wrona', 'oliwia.wrona@mc-demo.gov.pl', 'EVALUATED', FALSE, 'PRACOWNIK_SC_OKRESLONY', 6, 'Oliwia',
        'Wrona', 'Departament Cyfryzacji – Wydział Analiz i Danych', 'Młodszy specjalista', NULL, NULL, NULL),
       (21, 1, 'Grzegorz Sikora', 'grzegorz.sikora@mc-demo.gov.pl', 'EVALUATED', TRUE, 'URZEDNIK_SC', 6, 'Grzegorz',
        'Sikora', 'Departament Cyfryzacji – Wydział Analiz i Danych', 'Starszy specjalista', 2016, 2, DATE '2019-03-01');

INSERT INTO evaluation_history (user_id, office_id, cycle_id, outcome, level, evaluated_on, source, entered_by)
VALUES (21, 1, NULL, 'NEGATIVE', 'BELOW', DATE '2025-09-15', 'HISTORICAL', 4),
       (21, 1, NULL, 'NEGATIVE', 'BELOW', DATE '2026-03-16', 'HISTORICAL', 4);

SELECT setval(pg_get_serial_sequence('users', 'id'), (SELECT MAX(id) FROM users));
