-- Etap 8B: przygotowanie ocenianego do rozmowy (prywatne; przełożony widzi treść wyłącznie po udostępnieniu).
CREATE TABLE employee_self_prep (
    cycle_id              BIGINT PRIMARY KEY REFERENCES evaluation_cycles (id) ON DELETE CASCADE,
    achievements          TEXT,
    challenges            TEXT,
    development_needs     TEXT,
    shared_with_evaluator BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
