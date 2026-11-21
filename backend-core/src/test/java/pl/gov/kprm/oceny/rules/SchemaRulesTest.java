package pl.gov.kprm.oceny.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaRulesTest {
    private final ObjectMapper json = new ObjectMapper();
    private final DeadlineService deadlines = new DeadlineService();

    private JsonNode config(String s) throws Exception {
        return json.readTree(s);
    }

    @Test
    void missingSectionsFallBackToRegulationOf2016() throws Exception {
        SchemaRules r = SchemaRules.from(config("{\"criteria\": []}"));
        assertThat(r.initialInterviewDays()).isEqualTo(30);
        assertThat(r.appealDays()).isEqualTo(7);
        assertThat(r.appealDecisionDays()).isEqualTo(14);
        assertThat(r.reevaluationMonths()).isEqualTo(6);
        assertThat(r.level(42, 5)).as("średnia 8,4").isEqualTo(PerformanceLevel.SIGNIFICANTLY_ABOVE);
        assertThat(SchemaRules.from(null)).isEqualTo(SchemaRules.DEFAULT);
    }

    @Test
    void amendmentValuesDriveDeadlinesAndLevels() throws Exception {
        SchemaRules r = SchemaRules.from(config("""
                {"deadlines": {"initialInterviewDays": 14, "appealDays": 10, "reevaluationMonths": 12},
                 "levelThresholds": [2.5, 4.5, 6.5, 8, 9]}"""));
        assertThat(deadlines.initialInterviewDeadline(LocalDate.of(2027, 1, 4), false, r)).isEqualTo(LocalDate.of(2027, 1, 18));
        assertThat(deadlines.appealDeadline(LocalDate.of(2027, 1, 4), r)).isEqualTo(LocalDate.of(2027, 1, 14));
        assertThat(deadlines.reevaluationDue(LocalDate.of(2027, 1, 4), r)).isEqualTo(LocalDate.of(2028, 1, 4));
        assertThat(r.appealDecisionDays()).as("pole nieobecne — domyślne").isEqualTo(14);
        assertThat(r.level(31, 5)).as("średnia 6,2 przy progu 6,5").isEqualTo(PerformanceLevel.MEETS);
        assertThat(SchemaRules.DEFAULT.level(31, 5)).isEqualTo(PerformanceLevel.ABOVE);
    }

    @Test
    void invalidExplicitValuesAreRejectedOnSchemaCreationButIgnoredOnRead() throws Exception {
        JsonNode bad = config("{\"deadlines\": {\"appealDays\": 0}, \"levelThresholds\": [2, 4, 6, 9, 8]}");
        assertThat(SchemaRules.violations(bad)).hasSize(2);
        SchemaRules r = SchemaRules.from(bad);
        assertThat(r.appealDays()).isEqualTo(7);
        assertThat(r.levelThresholds()).containsExactly(SchemaRules.DEFAULT_THRESHOLDS);
        assertThat(SchemaRules.violations(config("{\"levelThresholds\": [2, 4, 6, 8, 8.5]}"))).as("ostatni próg = 9").hasSize(1);
        assertThat(SchemaRules.violations(config("{\"criteria\": []}"))).isEmpty();
    }
}
