package pl.gov.kprm.oceny.rules;

import pl.gov.kprm.oceny.domain.EvaluationCycle;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Odcisk treści arkusza (SHA-256) — podstawa pieczęci wewnętrznej i kodu weryfikacyjnego na wydruku hybrydowym.
 * Obejmuje treść oceny (bez danych zapoznania), więc kod na wydruku jest znany przed podpisem odręcznym.
 */
public final class SignatureStamp {
    static final int CODE_HEX_LENGTH = 12;
    private static final int CODE_GROUP = 4;

    private SignatureStamp() {
    }

    public static String fingerprint(EvaluationCycle c) {
        String canonical = String.join("|", "MERITUM-ARKUSZ-v1", s(c.getId()), s(c.getOfficeId()), s(c.getEmployeeId()),
                s(c.getEvaluatorId()), s(c.getSheetType()), s(c.getStartDate()), s(c.getDueDate()), s(c.getCriteriaJson()),
                s(c.getCriteriaExpectationsJson()), s(c.getScoresJson()), s(c.getAverageScore()), s(c.getPerformanceLevel()),
                s(c.getFinalResult()), s(c.getJustification()), s(c.getIprzNotes()), s(c.getGradeRequestJson()),
                s(c.getEvaluatedOn()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Brak SHA-256 w JVM", e);
        }
    }

    /** Kod do wpisania przy weryfikacji wydruku, np. „3FA2-91C0-77BE”. */
    public static String verificationCode(String fingerprint) {
        String head = fingerprint.substring(0, CODE_HEX_LENGTH).toUpperCase();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < head.length(); i += CODE_GROUP) {
            if (i > 0) code.append('-');
            code.append(head, i, i + CODE_GROUP);
        }
        return code.toString();
    }

    private static String s(Object o) {
        return Objects.toString(o, "");
    }
}
