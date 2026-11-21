"""Katalog kryteriów (załącznik nr 1 do rozporządzenia PRM z 4.04.2016, Dz. U. poz. 470) i działania rozwojowe do IPRZ."""
from typing import Dict, List

CRITERIA: Dict[str, str] = {
    "K01": "Komunikacja", "K02": "Kreatywność", "K03": "Negocjowanie",
    "K04": "Organizacja pracy i orientacja na osiąganie celów", "K05": "Orientacja na klienta/interesanta",
    "K06": "Podejmowanie decyzji i odpowiedzialność", "K07": "Radzenie sobie w sytuacjach kryzysowych",
    "K08": "Rzetelność", "K09": "Myślenie analityczne", "K10": "Wykorzystywanie wiedzy i doskonalenie zawodowe",
    "K11": "Współpraca", "K12": "Wystąpienia publiczne", "K13": "Zarządzanie ludźmi", "K14": "Zarządzanie strategiczne",
}
CRITERIA_NAMES: List[str] = list(CRITERIA.values())

# Propozycje form rozwoju (do wyboru i edycji przez oceniającego — nie są decyzją AI).
DEVELOPMENT_ACTIONS: Dict[str, List[str]] = {
    "Komunikacja": ["Warsztat z prostego języka w pismach urzędowych", "Informacja zwrotna od przełożonego do 3 kluczowych pism w kwartale"],
    "Kreatywność": ["Udział w zespole ds. usprawnień procesów", "Przegląd dobrych praktyk innych urzędów (benchmarking)"],
    "Negocjowanie": ["Szkolenie z technik negocjacyjnych", "Udział w negocjacjach jako obserwator z omówieniem po spotkaniu"],
    "Organizacja pracy i orientacja na osiąganie celów": ["Szkolenie z zarządzania czasem i priorytetami", "Cotygodniowy przegląd priorytetów z przełożonym przez 3 miesiące"],
    "Orientacja na klienta/interesanta": ["Szkolenie z obsługi klienta i komunikacji w trudnych sytuacjach", "Analiza skarg i wniosków interesantów z wdrożeniem usprawnień"],
    "Podejmowanie decyzji i odpowiedzialność": ["Samodzielne prowadzenie wybranej sprawy z mentoringiem", "Szkolenie z analizy ryzyka"],
    "Radzenie sobie w sytuacjach kryzysowych": ["Ćwiczenie symulacyjne procedury kryzysowej", "Szkolenie z zarządzania stresem i odporności psychicznej"],
    "Rzetelność": ["Lista kontrolna jakości przed przekazaniem dokumentu", "Szkolenie z procedur i standardów obowiązujących w komórce"],
    "Myślenie analityczne": ["Szkolenie z analizy danych (arkusze, wizualizacja)", "Przygotowanie analizy problemu z rekomendacjami pod opieką mentora"],
    "Wykorzystywanie wiedzy i doskonalenie zawodowe": ["Plan samokształcenia z kwartalnym przeglądem", "Udział w szkoleniu specjalistycznym i przekazanie wiedzy zespołowi"],
    "Współpraca": ["Udział w projekcie międzywydziałowym", "Rola mentora dla nowego pracownika"],
    "Wystąpienia publiczne": ["Warsztat wystąpień publicznych", "Prezentacja wyników pracy na spotkaniu departamentu"],
    "Zarządzanie ludźmi": ["Szkolenie z udzielania informacji zwrotnej i delegowania", "Coaching menedżerski (3–5 sesji)"],
    "Zarządzanie strategiczne": ["Udział w opracowaniu strategii/planu działalności urzędu", "Program rozwojowy dla kadry kierowniczej (np. KSAP)"],
}
