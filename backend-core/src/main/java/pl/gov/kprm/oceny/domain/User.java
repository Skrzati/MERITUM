package pl.gov.kprm.oceny.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long officeId;
    private String fullName;
    private String email;
    @Enumerated(EnumType.STRING)
    private Role role;
    private boolean isCivilServant;
    private Long supervisorId;
    private String firstName;
    private String lastName;
    private String department;
    private String positionTitle;
    private Integer appointmentYear;
    private Integer civilServiceGrade;
    private LocalDate lastGradeDate;
    /** false = konto zanonimizowane (dane osobowe usunięte, rekord zachowany dla kluczy obcych i audytu). */
    private boolean active = true;
}
