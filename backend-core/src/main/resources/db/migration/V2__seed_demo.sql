INSERT INTO offices (id, name, has_dg_role)
VALUES (1, 'Ministerstwo Cyfryzacji - Demo', TRUE);

INSERT INTO users (id, office_id, full_name, email, role, is_civil_servant, supervisor_id)
VALUES (1, 1, 'Anna Kowalska',      'anna.kowalska@mc-demo.gov.pl',      'DG',         TRUE,  NULL),
       (2, 1, 'Piotr Nowak',        'piotr.nowak@mc-demo.gov.pl',        'EVALUATOR',  TRUE,  1),
       (3, 1, 'Katarzyna Wiśniewska','katarzyna.wisniewska@mc-demo.gov.pl','EVALUATED', TRUE,  2),
       (4, 1, 'Marek Zieliński',    'marek.zielinski@mc-demo.gov.pl',    'HR',         TRUE,  1),
       (5, 1, 'Ewa Lewandowska',    'ewa.lewandowska@kprm-demo.gov.pl',  'KPRM_ADMIN', FALSE, NULL);

INSERT INTO schema_versions (id, version_code, effective_from, period_months, config_json, created_by)
VALUES (1, 'v1', DATE '2021-07-01', 24, $json$
{
  "legalBasis": "Rozporządzenie Prezesa Rady Ministrów w sprawie warunków i sposobu przeprowadzania ocen okresowych członków korpusu służby cywilnej",
  "active": true,
  "scale": [
    {"value": 1, "label": "nieodpowiedni"},
    {"value": 3, "label": "poniżej oczekiwań"},
    {"value": 5, "label": "zgodny z oczekiwaniami"},
    {"value": 7, "label": "powyżej oczekiwań"},
    {"value": 9, "label": "znacznie powyżej oczekiwań"}
  ],
  "criteriaRules": {"min": 5, "max": 7, "mandatoryCodes": ["K01", "K02"]},
  "resultThresholds": [
    {"from": 1.00, "to": 2.99, "result": "NEGATYWNA"},
    {"from": 3.00, "to": 9.00, "result": "POZYTYWNA"}
  ],
  "justificationRequiredForScores": [1, 9],
  "sheetTypes": {
    "A": "Arkusz oceny pracownika (stanowisko wykonawcze/specjalistyczne)",
    "B": "Arkusz oceny pracownika na stanowisku kierowniczym",
    "C": "Arkusz oceny pracownika w okresie służby przygotowawczej"
  },
  "criteria": [
    {"code": "K01", "name": "Rzetelność i terminowość",                  "mandatory": true,  "sheets": ["A","B","C"]},
    {"code": "K02", "name": "Postawa etyczna",                          "mandatory": true,  "sheets": ["A","B","C"]},
    {"code": "K03", "name": "Orientacja na obywatela i klienta",        "mandatory": false, "sheets": ["A","B","C"]},
    {"code": "K04", "name": "Współpraca i praca w zespole",             "mandatory": false, "sheets": ["A","B","C"]},
    {"code": "K05", "name": "Komunikacja",                              "mandatory": false, "sheets": ["A","B","C"]},
    {"code": "K06", "name": "Zarządzanie informacją",                   "mandatory": false, "sheets": ["A","B","C"]},
    {"code": "K07", "name": "Planowanie i organizacja pracy własnej",   "mandatory": false, "sheets": ["A","B","C"]},
    {"code": "K08", "name": "Doskonalenie zawodowe",                    "mandatory": false, "sheets": ["A","B","C"]},
    {"code": "K09", "name": "Myślenie analityczne",                     "mandatory": false, "sheets": ["A","B"]},
    {"code": "K10", "name": "Kreatywność i innowacyjność",              "mandatory": false, "sheets": ["A","B"]},
    {"code": "K11", "name": "Radzenie sobie w sytuacjach stresowych",   "mandatory": false, "sheets": ["A","B","C"]},
    {"code": "K12", "name": "Wiedza specjalistyczna",                   "mandatory": false, "sheets": ["A","B","C"]},
    {"code": "K13", "name": "Podejmowanie decyzji",                     "mandatory": false, "sheets": ["B"]},
    {"code": "K14", "name": "Zarządzanie zespołem i zasobami",          "mandatory": false, "sheets": ["B"]}
  ]
}
$json$::jsonb, 5);

INSERT INTO evaluation_cycles (id, office_id, employee_id, evaluator_id, schema_version_id, sheet_type, status,
                               start_date, due_date, criteria_json)
VALUES (1, 1, 3, 2, 1, 'A', 'OBSERVATION', DATE '2025-01-01', DATE '2026-12-31', $json$
[
  {"code": "K01", "name": "Rzetelność i terminowość"},
  {"code": "K02", "name": "Postawa etyczna"},
  {"code": "K04", "name": "Współpraca i praca w zespole"},
  {"code": "K07", "name": "Planowanie i organizacja pracy własnej"},
  {"code": "K11", "name": "Radzenie sobie w sytuacjach stresowych"},
  {"code": "K12", "name": "Wiedza specjalistyczna"}
]
$json$::jsonb),
       (2, 1, 2, 1, 1, 'B', 'DRAFT_CRITERIA', DATE '2026-11-01', DATE '2028-10-31', NULL);

INSERT INTO employee_notes (cycle_id, author_id, content, is_shared_after_eval, created_at)
VALUES (1, 2, 'Pracownica przepracowała 22 nadgodziny w marcu przy wdrożeniu KSeF w urzędzie. Terminy dotrzymane mimo dodatkowego obciążenia.', TRUE, TIMESTAMPTZ '2025-03-31 16:00:00+02'),
       (1, 2, 'Przejęła obowiązki nieobecnego członka zespołu (L4, 6 tygodni). Ryzyko przeciążenia - do omówienia rozłożenie zadań.', TRUE, TIMESTAMPTZ '2025-06-12 10:30:00+02'),
       (1, 3, 'Zgłaszam przeciążenie: równolegle 3 projekty o wysokim priorytecie, brak czasu na szkolenia z IPRZ.', FALSE, TIMESTAMPTZ '2025-09-03 09:15:00+02'),
       (1, 2, 'Rozmowa wspierająca przeprowadzona; ustalono przesunięcie jednego projektu i szkolenie z zarządzania czasem w Q1 2026.', TRUE, TIMESTAMPTZ '2025-09-10 14:00:00+02');

INSERT INTO audit_logs (office_id, actor_id, action, entity_type, entity_id, payload_json)
VALUES (1, 5, 'SCHEMA_PUBLISHED', 'SCHEMA_VERSION', 1, '{"versionCode": "v1"}'),
       (1, 2, 'CRITERIA_CONFIRMED', 'EVALUATION_CYCLE', 1, '{"criteriaCount": 6}'),
       (1, 2, 'STATUS_CHANGED', 'EVALUATION_CYCLE', 1, '{"from": "CRITERIA_CONFIRMED", "to": "OBSERVATION"}'),
       (1, 1, 'CYCLE_CREATED', 'EVALUATION_CYCLE', 2, '{"sheetType": "B"}');

SELECT setval(pg_get_serial_sequence('offices', 'id'), (SELECT MAX(id) FROM offices));
SELECT setval(pg_get_serial_sequence('users', 'id'), (SELECT MAX(id) FROM users));
SELECT setval(pg_get_serial_sequence('schema_versions', 'id'), (SELECT MAX(id) FROM schema_versions));
SELECT setval(pg_get_serial_sequence('evaluation_cycles', 'id'), (SELECT MAX(id) FROM evaluation_cycles));
