# Analiza oficjalnych materiałów KPRM — Cyfrowy Asystent Ocen Okresowych

> Data analizy: 21.11.2026. Źródła: pakiet „KPRM – Cyfrowy asystent ocen – aktualne” (akty prawne, przypadki użycia,
> schemat procesu BPMN, formularze A/B/C ze schematami pól, przykładowe wypełnione arkusze, materiały dodatkowe)
> oraz opis wyzwania i regulamin konkursu.

## 1. Przeanalizowane pliki

| Folder | Pliki | Uwagi |
|---|---|---|
| `1. Akty prawne` | Rozporządzenie PRM z 4.04.2016 (Dz.U. poz. 470), wyciąg z ustawy o SC (t.j. Dz.U. 2026 poz. 590), pełna ustawa, Standardy ZZL | rozporządzenie z wzorem arkusza A przeanalizowane w całości |
| `2. Przypadki użycia` | opis, warunki brzegowe, diagram A3 | |
| `3. Schemat procesu` | BPMN (Bizagi `.bpm`, `.xpdl`, `.xml`, `.png`), skrócony opis procesu | 43 czynności, tory: ADMIN, OCENIAJĄCY, OCENIANY, SCHEDULER (CZAS), ZARZĄDZAJĄCY |
| `4. Formularze ocen` | wzory A/B/C (PDF), schematy pól A/B/C (docx), Arkusz B (xlsx) | wzory PDF to skany — pola opisane w schematach docx |
| `5. Przykłady` | 10 arkuszy docx + 3 arkusze z poradnika (PDF) | |
| `6. Materiały dodatkowe` | liczenie terminów, Poradnik oceny okresowe w SC | |

Kryteria jury: związek z wyzwaniem i zgodność z prawem 30%, potencjał wdrożeniowy 30%, oryginalność 20%, kompletność 20%.
Na demo wymagane jest pokazanie całego procesu sporządzenia oceny.

## 2. Kryteria (V4) vs Załącznik nr 1

- Nazwy 14 kryteriów, kryteria obowiązkowe A (6) / B (5) / C (5) i ich kolejność (§ 4) oraz zakresy arkuszy (§ 3) — zgodne.
- Brakowało opisów zachowań (2–6 punktów na kryterium) — dodane w V5 (`criteria[].description`).
- Pouczenie: art. 83 ust. 1 ustawy o SC — zweryfikowane w oficjalnym tekście.

## 3. Wydruk (`SheetHtmlRenderer`) vs oficjalny wzór

| Część | Wymagane przez wzór/schemat pól |
|---|---|
| Nagłówek | „ARKUSZ X — OCENA OKRESOWA URZĘDNIKA SŁUŻBY CYWILNEJ LUB PRACOWNIKA SŁUŻBY CYWILNEJ” |
| Cz. I | nazwa urzędu; imię, nazwisko, komórka organizacyjna, stanowisko — ocenianego i oceniającego; kryteria obowiązkowe i dodatkowe (2 kolumny); termin sporządzenia oceny; formuła „Potwierdzamy…” (§ 5 ust. 1); miejscowość, data, podpis obu stron |
| Cz. II | data rozmowy oceniającej; okres od (zapoznanie z kryteriami) do (sporządzenie oceny); macierz ocen 1/3/5/7/9 (obowiązkowe + dodatkowe / „brak”); średnia z przecinkiem; tabela „PRZYZNANIE OCENY” z X w przedziale; X POZYTYWNA/NEGATYWNA; uzasadnienie; wnioski IPRZ; dane poprzedniej oceny (urzędnik); „Czy dołączono wniosek? TAK / NIE / NIE DOTYCZY”; dane oceniającego + podpis; oświadczenie ocenianego + podpis; pouczenie z art. 83 ust. 1 |
| Cz. III | imię, nazwisko, rok mianowania, posiadany stopień, data przyznania stopnia; formuła wniosku art. 89 ust. 1 z kolejnym stopniem; 9. stopień → komunikat o najwyższym stopniu; umotywowanie; podpis oceniającego |

## 4. Luki procesowe (przepisy / use cases / BPMN)

- § 11 ust. 5: wniosek o stopień tylko przy ocenie pozytywnej urzędnika mianowanego.
- „Liczenie terminów”: kolejne usprawiedliwione nieobecności rozdzielone wyłącznie dniami wolnymi są jedną nieobecnością ciągłą.
- Art. 81 ust. 8 + § 8: 4 tryby zmiany terminu (warunek > 6 mies.), pisemne zawiadomienie; wniosek ocenianego + decyzja oceniającego.
- Art. 81 ust. 9 / § 7: ponowna ocena 6 mies. od zapoznania z oceną negatywną; kryteria w 7 dni.
- Art. 83 ust. 3: DG rozpatruje sprzeciw w 14 dni; ust. 5: odwołanie do sądu pracy w 14 dni.
- Art. 89 ust. 2: obligatoryjny stopień po dwóch kolejnych ocenach na dwóch najwyższych poziomach (30 dni).
- § 9 ust. 1 / art. 81 ust. 6: zmiana stanowiska z istotną zmianą zakresu obowiązków.
- Blokady pól po etapach + tryb korekty przez administratora; scheduler przypomnień; oceny historyczne; zastępstwa; usunięcie danych; sprawozdawczość.

## 5. Walidacja `consistency-check` na arkuszach wzorcowych

Wykryte błędy umieszczone w przykładach KPRM:

| Arkusz | Błąd | Kod |
|---|---|---|
| 5) radca | umotywowanie wniosku Romana Kowala mówi o „Panu Adamie Kowaliku” | FOREIGN_NAME |
| 5) radca | „ocena końcowa znacznie powyżej oczekiwań” przy średniej 6,3 | LEVEL_MISMATCH |
| 13) dyrektor dep. | „Kreatywność” w cz. I, brak w cz. II | CRITERIA_MISMATCH |
| 13) dyrektor dep. | oceniany Innocenty — „oceniana nie otrzymała” | GENDER_MISMATCH |
| 1) GLW | oceniana Janina — „Pracownik spełniał… oceniany wdrożył” | GENDER_MISMATCH |

Fałszywe alarmy (naprawione w Etapie 5): tytuł po „Pan/Pani” („Pan Dyrektor”), oboczność ó→o („Wzór → Wzora”),
poziom przy pojedynczym kryterium, czasownik odnoszący się do rzeczownika („zespół pracował”), brak „oceniany/oceniana”
w słownikach płci, średnia z przecinkiem w tekście. Arkusze są fixtures testu regresji w `ai-gateway/tests`.

## 6. Zakres Etapu 5 (zatwierdzony)

V5: pola urzędowe (`first_name`, `last_name`, `department`, `position_title`, `appointment_year`, `civil_service_grade`,
`last_grade_date`, `offices.city`, daty rozmów), `evaluation_history`, `wellbeing_surveys`, `scope_changes`,
`reschedule_requests`, opisy kryteriów, bogaty seed; poprawki reguł; nowe endpointy; JWT gov-login; nagłówki
bezpieczeństwa; PDF 1:1; k-anonimizacja k=5 po stronie serwera.
