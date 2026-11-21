from app.consistency import check, level_for_average

CRITERIA = ["Rzetelność", "Współpraca", "Komunikacja", "Organizacja pracy i orientacja na osiąganie celów",
            "Wykorzystywanie wiedzy i doskonalenie zawodowe"]


def run(**overrides):
    args = dict(employee_name="Katarzyna Wiśniewska", employee_gender="K", criteria_part1=CRITERIA,
                criteria_part2=CRITERIA, average_score=5.4, level="MEETS",
                justification="Pracownica rzetelnie realizowała zadania na poziomie oczekiwań.",
                grade_request_text=None)
    args.update(overrides)
    return {w.code: w for w in check(**args)}


def test_clean_sheet_has_no_warnings():
    assert run() == {}


def test_criteria_mismatch_between_parts():
    warnings = run(criteria_part2=CRITERIA[:4] + ["Kreatywność"])
    assert "CRITERIA_MISMATCH" in warnings
    assert "kreatywność" in warnings["CRITERIA_MISMATCH"].message


def test_foreign_name_in_justification():
    warnings = run(justification="Pani Katarzyna realizowała zadania. Jan Kowalski wykazał się inicjatywą.")
    assert "FOREIGN_NAME" in warnings
    assert "Jan Kowalski" in warnings["FOREIGN_NAME"].message


def test_inflected_own_name_is_not_foreign():
    assert "FOREIGN_NAME" not in run(justification="Praca Katarzyny Wiśniewskiej była rzetelna.")


def test_kprm_example_average_63_is_above_not_significantly_above():
    warnings = run(average_score=6.3, level="ABOVE",
                   justification="Pracownica realizowała zadania znacznie powyżej oczekiwań.")
    assert level_for_average(6.3) == "ABOVE"
    assert "LEVEL_MISMATCH" in warnings
    assert "znacznie powyżej oczekiwań" in warnings["LEVEL_MISMATCH"].message


def test_declared_level_inconsistent_with_average():
    assert "LEVEL_MISMATCH" in run(average_score=4.2, level="znacznie powyżej oczekiwań", justification="")


def test_duplicate_paragraphs():
    para = "Pracownica terminowo realizowała wszystkie powierzone zadania w zespole."
    assert "DUPLICATE_TEXT" in run(justification=para + "\n\n" + para)


def test_grade_request_copied_from_justification():
    text = "Pracownica wyróżniała się zaangażowaniem i wiedzą specjalistyczną."
    assert "DUPLICATE_TEXT" in run(justification=text, grade_request_text=text)


def test_male_forms_for_female_employee():
    warnings = run(justification="Pan Wiśniewska wykazał się dużym zaangażowaniem i zrealizował plan.")
    assert "GENDER_MISMATCH" in warnings
    assert "wykazał" in warnings["GENDER_MISMATCH"].message


def test_department_word_is_not_a_male_verb():
    assert "GENDER_MISMATCH" not in run(justification="Pracownica brała udział w pracach wydziału i miała przydział zadań.")
