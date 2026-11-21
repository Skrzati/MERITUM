-- Korekta schematu v1 do § 3-5 rozporządzenia (Dz.U. 2016 poz. 470):
-- kryteria obowiązkowe zależne od arkusza (A: 6, B: 5, C: 5), dodatkowe (0-3) wyłącznie z katalogu (załącznik nr 1).
UPDATE schema_versions
SET config_json = $json$
{
  "legalBasis": "Rozporządzenie Prezesa Rady Ministrów z dnia 4 kwietnia 2016 r. w sprawie warunków i sposobu przeprowadzania ocen okresowych urzędników służby cywilnej i pracowników służby cywilnej (Dz.U. 2016 poz. 470)",
  "active": true,
  "scale": [
    {"value": 1, "label": "znacznie poniżej oczekiwań"},
    {"value": 3, "label": "poniżej oczekiwań"},
    {"value": 5, "label": "na poziomie oczekiwań"},
    {"value": 7, "label": "powyżej oczekiwań"},
    {"value": 9, "label": "znacznie powyżej oczekiwań"}
  ],
  "sheetTypes": {
    "A": "Kierownik urzędu (§ 3 ust. 2)",
    "B": "Stanowisko polegające na bezpośrednim kierowaniu pracownikami (§ 3 ust. 3)",
    "C": "Pozostałe stanowiska (§ 3 ust. 4)"
  },
  "criteriaRules": {"maxAdditional": 3, "allowCustom": false},
  "criteria": [
    {"code": "K01", "name": "Komunikacja"},
    {"code": "K02", "name": "Kreatywność"},
    {"code": "K03", "name": "Negocjowanie"},
    {"code": "K04", "name": "Organizacja pracy i orientacja na osiąganie celów"},
    {"code": "K05", "name": "Orientacja na klienta/interesanta"},
    {"code": "K06", "name": "Podejmowanie decyzji i odpowiedzialność"},
    {"code": "K07", "name": "Radzenie sobie w sytuacjach kryzysowych"},
    {"code": "K08", "name": "Rzetelność"},
    {"code": "K09", "name": "Myślenie analityczne"},
    {"code": "K10", "name": "Wykorzystywanie wiedzy i doskonalenie zawodowe"},
    {"code": "K11", "name": "Współpraca"},
    {"code": "K12", "name": "Wystąpienia publiczne"},
    {"code": "K13", "name": "Zarządzanie ludźmi"},
    {"code": "K14", "name": "Zarządzanie strategiczne"}
  ],
  "mandatoryBySheet": {
    "A": ["K13", "K06", "K04", "K11", "K01", "K14"],
    "B": ["K13", "K06", "K04", "K11", "K01"],
    "C": ["K10", "K08", "K04", "K11", "K01"]
  }
}
$json$::jsonb
WHERE version_code = 'v1';

-- Cykl 1: oceniana na stanowisku wykonawczym -> arkusz C (5 obowiązkowych + 1 dodatkowe z katalogu).
UPDATE evaluation_cycles
SET sheet_type    = 'C',
    criteria_json = $json$
[
  {"code": "K10", "name": "Wykorzystywanie wiedzy i doskonalenie zawodowe", "mandatory": true},
  {"code": "K08", "name": "Rzetelność", "mandatory": true},
  {"code": "K04", "name": "Organizacja pracy i orientacja na osiąganie celów", "mandatory": true},
  {"code": "K11", "name": "Współpraca", "mandatory": true},
  {"code": "K01", "name": "Komunikacja", "mandatory": true},
  {"code": "K07", "name": "Radzenie sobie w sytuacjach kryzysowych", "mandatory": false}
]
$json$::jsonb
WHERE id = 1;
