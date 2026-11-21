from app.pii import mask_pii, unmask_pii, PiiMasker


def test_masks_known_name_including_inflected_forms():
    text = "Katarzyna Wiśniewska przejęła zadania. Rozmowa z Katarzyną Wiśniewską odbyła się w marcu."
    result = mask_pii(text, known_names=["Katarzyna Wiśniewska"])

    assert "Wiśniewsk" not in result.masked and "Katarzyn" not in result.masked
    assert result.masked.count("[OSOBA_1]") == 2
    assert result.mapping["[OSOBA_1]"] == "Katarzyna Wiśniewska"


def test_masks_pesel_email_and_unit():
    text = "PESEL 85010112345, e-mail k.wisniewska@mc.gov.pl, Departament Cyfryzacji Usług."
    result = mask_pii(text)

    assert "85010112345" not in result.masked
    assert "@" not in result.masked
    assert "Cyfryzacji" not in result.masked
    assert "[PESEL_1]" in result.masked and "[EMAIL_1]" in result.masked and "[KOMORKA_1]" in result.masked


def test_detects_unknown_people_by_first_name_and_honorific():
    result = mask_pii("Zadanie przejął Tomasz Lewicki, a Pani Nowacka zatwierdziła plan.",
                      known_names=["Katarzyna Wiśniewska"])

    assert "Lewicki" not in result.masked and "Nowacka" not in result.masked
    assert "Pani [OSOBA_" in result.masked


def test_unmask_restores_original():
    original = "Anna Kowalska (anna@urzad.gov.pl) wdrożyła system."
    result = mask_pii(original, known_names=["Anna Kowalska"])

    assert unmask_pii(result.masked, result.mapping) == original


def test_same_person_same_token_across_texts():
    masker = PiiMasker(known_names=["Piotr Nowak"])
    first, second = masker.mask("Piotr Nowak ocenia."), masker.mask("Notatka Piotra Nowaka.")

    assert first.startswith("[OSOBA_1]") and "[OSOBA_1]" in second
    assert len([t for t in masker.mapping if t.startswith("[OSOBA")]) == 1


def test_does_not_mask_regular_capitalised_words():
    result = mask_pii("Wdrożenie KSeF zakończono w terminie. Szkolenie odbyło się w Warszawie.")
    assert "[OSOBA" not in result.masked
