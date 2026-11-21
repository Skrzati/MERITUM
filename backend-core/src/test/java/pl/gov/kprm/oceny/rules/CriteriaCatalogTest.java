package pl.gov.kprm.oceny.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.gov.kprm.oceny.domain.SheetType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Weryfikuje konfigurację schematu v1 dokładnie w postaci z migracji V5 (ostatnia wersja v1). */
class CriteriaCatalogTest {
    private static final Path V5 = Path.of("src/main/resources/db/migration/V5__kprm_alignment_and_demo_data.sql");
    private static final String JSON_QUOTE = "$json$";

    private static CriteriaCatalog v1;

    @BeforeAll
    static void loadSchemaFromMigration() throws Exception {
        String sql = Files.readString(V5);
        int start = sql.indexOf(JSON_QUOTE) + JSON_QUOTE.length();
        JsonNode config = new ObjectMapper().readTree(sql.substring(start, sql.indexOf(JSON_QUOTE, start)));
        v1 = CriteriaCatalog.from(config);
    }

    @Test
    void catalogHas14CriteriaWithAnnexDescriptions() throws Exception {
        assertThat(v1.criteria()).hasSize(14);
        String sql = Files.readString(V5);
        JsonNode config = new ObjectMapper().readTree(sql.substring(sql.indexOf(JSON_QUOTE) + JSON_QUOTE.length(),
                sql.indexOf(JSON_QUOTE, sql.indexOf(JSON_QUOTE) + 1)));
        config.path("criteria").forEach(c -> assertThat(c.path("description").size()).as(c.path("code").asText()).isGreaterThan(1));
    }

    @Test
    void mandatoryCriteriaDependOnSheet() {
        assertThat(codes(v1.mandatoryFor(SheetType.A))).containsExactly("K13", "K06", "K04", "K11", "K01", "K14");
        assertThat(codes(v1.mandatoryFor(SheetType.B))).containsExactly("K13", "K06", "K04", "K11", "K01");
        assertThat(codes(v1.mandatoryFor(SheetType.C))).containsExactly("K10", "K08", "K04", "K11", "K01");
    }

    @Test
    void selectsMandatoryPlusUpToThreeFromCatalog() {
        List<Criterion> selected = v1.select(SheetType.C, List.of(code("k07"), code("K09"), code("K13")));

        assertThat(selected).hasSize(8);
        assertThat(selected.subList(5, 8)).extracting(Criterion::name)
                .containsExactly("Radzenie sobie w sytuacjach kryzysowych", "Myślenie analityczne", "Zarządzanie ludźmi");
        assertThat(selected.subList(5, 8)).noneMatch(Criterion::mandatory);
    }

    @Test
    void zeroAdditionalIsAllowed() {
        assertThat(v1.select(SheetType.B, List.of())).hasSize(5);
    }

    @Test
    void rejectsMoreThanThreeAdditional() {
        assertThatThrownBy(() -> v1.select(SheetType.C, List.of(code("K02"), code("K03"), code("K05"), code("K07"))))
                .hasMessageContaining("maksymalnie 3");
    }

    @Test
    void rejectsCustomCriterionOutsideCatalogInV1() {
        assertThatThrownBy(() -> v1.select(SheetType.C, List.of(new Criterion(null, "Postawa etyczna", false))))
                .hasMessageContaining("wyłącznie kryteria z załącznika nr 1");
    }

    @Test
    void rejectsUnknownCode() {
        assertThatThrownBy(() -> v1.select(SheetType.C, List.of(code("K99"))))
                .hasMessageContaining("nie należy do katalogu");
    }

    @Test
    void rejectsDuplicatingMandatoryForSheet() {
        assertThatThrownBy(() -> v1.select(SheetType.C, List.of(code("K08"))))
                .hasMessageContaining("już obowiązkowe dla arkusza C");
        assertThat(v1.select(SheetType.A, List.of(code("K08")))).hasSize(7);
    }

    @Test
    void rejectsRepeatedAdditional() {
        assertThatThrownBy(() -> v1.select(SheetType.C, List.of(code("K02"), code("k02"))))
                .hasMessageContaining("powtarza się");
    }

    @Test
    void rejectsConfigWithoutMandatoryForEverySheet() throws Exception {
        JsonNode broken = new ObjectMapper().readTree("""
                {"criteria": [{"code": "K01", "name": "Komunikacja"}], "mandatoryBySheet": {"A": ["K01"], "B": ["K02"]}}
                """);
        assertThatThrownBy(() -> CriteriaCatalog.from(broken))
                .isInstanceOf(RuleViolationException.class)
                .hasMessageContaining("B: kryterium obowiązkowe K02 spoza katalogu")
                .hasMessageContaining("mandatoryBySheet.C");
    }

    private static Criterion code(String code) {
        return new Criterion(code, null, false);
    }

    private static List<String> codes(List<Criterion> criteria) {
        return criteria.stream().map(Criterion::code).toList();
    }
}
