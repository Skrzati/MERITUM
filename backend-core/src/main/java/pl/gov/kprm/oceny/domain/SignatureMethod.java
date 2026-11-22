package pl.gov.kprm.oceny.domain;

/**
 * Sposób podpisania arkusza — trzy tryby; dawne wartości pozostają dla istniejących arkuszy i klientów API i są
 * odwzorowane na tryb przez {@link #mode()}.
 */
public enum SignatureMethod {
    /** Podpis kwalifikowany (QES), podpis zaufany (Profil Zaufany) lub podpis w mObywatelu. */
    QUALIFIED_OR_TRUSTED,
    /** Wewnętrzna pieczęć systemu: skrót SHA-256 treści arkusza + znacznik czasu w dzienniku audytu. */
    INTERNAL_AUDIT_STAMP,
    /** Wydruk PDF z kodem weryfikacyjnym, podpis odręczny (data) i opcjonalna referencja skanu. */
    HYBRID_PAPER_SCAN,
    @Deprecated MOBYWATEL_QES,
    @Deprecated PAPER_HYBRID,
    @Deprecated SYSTEM_DEMO;

    public SignatureMethod mode() {
        return switch (this) {
            case MOBYWATEL_QES -> QUALIFIED_OR_TRUSTED;
            case PAPER_HYBRID -> HYBRID_PAPER_SCAN;
            case SYSTEM_DEMO -> INTERNAL_AUDIT_STAMP;
            default -> this;
        };
    }
}
