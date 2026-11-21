-- Ankieta dobrostanu technicznie anonimowa: brak powiązania z osobą, czas zgłoszenia z dokładnością do dnia
-- (godzina w małym urzędzie mogłaby wskazać respondenta). Agregaty liczone wyłącznie po office_id.
ALTER TABLE wellbeing_surveys DROP COLUMN user_id;
UPDATE wellbeing_surveys SET submitted_at = date_trunc('day', submitted_at);
ALTER TABLE wellbeing_surveys ALTER COLUMN submitted_at SET DEFAULT date_trunc('day', now());

-- Cykl #9 (demo): uzasadnienie i IPRZ spójne z oceną 1 pkt w kryterium K10 (pozostałe kryteria na poziomie oczekiwań).
UPDATE evaluation_cycles
SET justification = 'Oceniana poprawnie realizuje bieżące zadania, jednak w całym okresie oceny nie aktualizowała wiedzy '
        || 'specjalistycznej: nie uczestniczyła w zaplanowanych szkoleniach, a w opracowaniach stosowała nieaktualne '
        || 'przepisy, co wymagało wielokrotnych korekt. Ocena cząstkowa na poziomie znacznie poniżej oczekiwań w kryterium '
        || '„Wykorzystywanie wiedzy i doskonalenie zawodowe” (K10) skutkuje oceną negatywną.',
    iprz_notes    = 'Indywidualny plan samokształcenia z kwartalnym przeglądem postępów, udział w szkoleniu z aktualnych '
        || 'zmian przepisów w obszarze zadań oraz bieżąca weryfikacja podstaw prawnych w opracowaniach.'
WHERE id = 9;
