package pl.gov.kprm.oceny.domain;

/** Kto jest powiadamiany o ocenie po terminie: 1–7 dni oceniający, 8–14 zastępca, 15–30 kadry, > 30 dyrektor generalny. */
public enum EscalationLevel { OCENIAJACY, ZASTEPCA, KADRY, DYREKTOR_GENERALNY }
