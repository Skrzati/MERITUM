-- Kto faktycznie sporządził ocenę (oceniający albo jego zastępca) — drukowane przy podpisie oceniającego w cz. II arkusza.
-- Istniejące sporządzone oceny: przypisany oceniający (zastępstwa przed V16 widoczne tylko w audycie, substituteFor).
ALTER TABLE evaluation_cycles ADD COLUMN evaluated_by BIGINT REFERENCES users (id);

UPDATE evaluation_cycles SET evaluated_by = evaluator_id WHERE evaluated_on IS NOT NULL;
