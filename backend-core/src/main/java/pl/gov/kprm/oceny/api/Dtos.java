package pl.gov.kprm.oceny.api;

import com.fasterxml.jackson.databind.JsonNode;
import pl.gov.kprm.oceny.domain.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class Dtos {
    private Dtos() {
    }

    public record UserDto(Long id, Long officeId, String fullName, String email, Role role,
                          boolean civilServant, Long supervisorId) {
        public static UserDto of(User u) {
            return new UserDto(u.getId(), u.getOfficeId(), u.getFullName(), u.getEmail(), u.getRole(),
                    u.isCivilServant(), u.getSupervisorId());
        }
    }

    public record CycleDto(Long id, Long officeId, Long employeeId, Long evaluatorId, Long schemaVersionId,
                           SheetType sheetType, CycleStatus status, LocalDate startDate, LocalDate dueDate,
                           JsonNode criteria, JsonNode scores, BigDecimal averageScore, String finalResult) {
        public static CycleDto of(EvaluationCycle c) {
            return new CycleDto(c.getId(), c.getOfficeId(), c.getEmployeeId(), c.getEvaluatorId(),
                    c.getSchemaVersionId(), c.getSheetType(), c.getStatus(), c.getStartDate(), c.getDueDate(),
                    c.getCriteriaJson(), c.getScoresJson(), c.getAverageScore(), c.getFinalResult());
        }
    }
}
