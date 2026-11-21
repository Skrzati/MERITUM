-- Etap 3: archiwizacja arkuszy w części B akt osobowych (retencja 10 / 50 lat, legal hold), czyszczenie danych
-- pomocniczych po zamknięciu cyklu (notatki robocze, ankiety — zakaz „czarnych teczek”) i opis stanowiska (art. 84).

-- Data nawiązania stosunku pracy: po 31.12.2018 — 10 lat od końca roku rozwiązania stosunku pracy, wcześniej 50 lat
-- (art. 94 pkt 9b i art. 94^4 KP; ustawa z 10.01.2018). NULL = kategoria ostrożnościowa 50 lat.
ALTER TABLE users
    ADD COLUMN employment_start_date DATE,
    ADD COLUMN job_description       TEXT CHECK (char_length(job_description) <= 10000);

-- Znacznik czyszczenia danych pomocniczych cyklu; arkusz, historia ocen i audyt pozostają nienaruszone.
ALTER TABLE evaluation_cycles ADD COLUMN working_notes_purged_at TIMESTAMPTZ;

-- Seed demo: daty zatrudnienia (obie kategorie retencji) i opisy stanowisk.
UPDATE users SET employment_start_date = DATE '2015-03-01' WHERE id IN (1, 2);
UPDATE users SET employment_start_date = DATE '2019-02-01' WHERE id IN (3, 9);
UPDATE users SET employment_start_date = DATE '2021-10-01' WHERE id IN (8, 10, 13);
UPDATE users SET job_description = 'Analiza wymagań i koordynacja wdrożeń systemów teleinformatycznych; współpraca '
    || 'z wykonawcami i odbiór prac; prowadzenie dokumentacji projektowej; prezentowanie wyników kierownictwu.'
WHERE id = 3;
UPDATE users SET job_description = 'Przygotowywanie analiz danych i raportów dla kierownictwa; weryfikacja jakości '
    || 'danych; udział w zespołach zadaniowych; negocjowanie zakresu prac z komórkami merytorycznymi.'
WHERE id = 9;
