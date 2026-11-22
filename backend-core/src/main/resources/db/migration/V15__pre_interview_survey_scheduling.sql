-- Ankieta przed rozmową: przełożony decyduje, od kiedy oceniany ją widzi (opens_on), może dodać wstęp i termin odpowiedzi.
-- Istniejące ankiety były dostępne od razu — zachowują to (opens_on = data ostatniej zmiany).
ALTER TABLE pre_interview_surveys
    ADD COLUMN opens_on        DATE,
    ADD COLUMN answer_deadline DATE,
    ADD COLUMN intro_message   VARCHAR(2000),
    ADD COLUMN scheduled_by    BIGINT REFERENCES users (id),
    ADD CONSTRAINT pre_interview_surveys_deadline_check
        CHECK (answer_deadline IS NULL OR (opens_on IS NOT NULL AND answer_deadline >= opens_on));

UPDATE pre_interview_surveys SET opens_on = updated_at::date WHERE opens_on IS NULL;
