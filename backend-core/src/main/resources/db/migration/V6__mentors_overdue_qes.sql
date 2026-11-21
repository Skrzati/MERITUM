-- Etap 6A (ustalenia z mentorami KPRM): termin oceny nie blokuje jej sporządzenia (rejestrujemy przyczynę opóźnienia),
-- metoda podpisu arkusza (mObywatel QES / ścieżka hybrydowa papierowa / demo) oraz demo cykli po terminie (eskalacja).
ALTER TABLE evaluation_cycles
    ADD COLUMN delay_reason     TEXT,
    ADD COLUMN signature_method VARCHAR(32) CHECK (signature_method IN ('MOBYWATEL_QES', 'PAPER_HYBRID', 'SYSTEM_DEMO'));

-- Dwa aktywne cykle po terminie (eskalacja: 8–14 dni -> zastępca, > 30 dni -> dyrektor generalny przy dacie demo 21.11.2026).
INSERT INTO evaluation_cycles (id, office_id, employee_id, evaluator_id, schema_version_id, sheet_type, status, start_date,
                               due_date, criteria_json, criteria_interview_date, criteria_confirmed_on, place)
VALUES (16, 1, 8, 2, 1, 'C', 'OBSERVATION', DATE '2024-11-08', DATE '2026-11-10', '[{"code": "K10", "name": "Wykorzystywanie wiedzy i doskonalenie zawodowe", "mandatory": true}, {"code": "K08", "name": "Rzetelność", "mandatory": true}, {"code": "K04", "name": "Organizacja pracy i orientacja na osiąganie celów", "mandatory": true}, {"code": "K11", "name": "Współpraca", "mandatory": true}, {"code": "K01", "name": "Komunikacja", "mandatory": true}]'::jsonb,
        DATE '2024-11-15', DATE '2024-11-18', 'Warszawa'),
       (17, 1, 12, 2, 1, 'C', 'EVALUATION_DRAFT', DATE '2024-10-15', DATE '2026-10-15', '[{"code": "K10", "name": "Wykorzystywanie wiedzy i doskonalenie zawodowe", "mandatory": true}, {"code": "K08", "name": "Rzetelność", "mandatory": true}, {"code": "K04", "name": "Organizacja pracy i orientacja na osiąganie celów", "mandatory": true}, {"code": "K11", "name": "Współpraca", "mandatory": true}, {"code": "K01", "name": "Komunikacja", "mandatory": true}]'::jsonb,
        DATE '2024-10-22', DATE '2024-10-23', 'Warszawa');
UPDATE evaluation_cycles SET scores_json = '{"K10": 5, "K08": 5, "K04": 3}'::jsonb WHERE id = 17;

SELECT setval(pg_get_serial_sequence('evaluation_cycles', 'id'), (SELECT MAX(id) FROM evaluation_cycles));
