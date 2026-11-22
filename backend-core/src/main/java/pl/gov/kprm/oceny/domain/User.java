package pl.gov.kprm.oceny.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
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
    /** Kolumna utrzymywana dla zgodności (raporty, import); źródłem prawdy jest {@link #getEmploymentStatus()}. */
    @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE)
    private boolean isCivilServant;
    @Enumerated(EnumType.STRING)
    @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE)
    private EmploymentStatus employmentStatus;
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
    /** Data nawiązania stosunku pracy — kategoria retencji akt (10 / 50 lat). */
    private LocalDate employmentStartDate;
    /** Opis stanowiska pracy (art. 84 ustawy o SC) — kontekst dla podpowiedzi kryteriów. */
    private String jobDescription;
    /** Role dodatkowe (mały urząd: kierownik łączy np. DG + EVALUATOR + HR); bez KPRM_ADMIN. */
    @Convert(converter = RoleSetConverter.class)
    private java.util.Set<Role> secondaryRoles = java.util.EnumSet.noneOf(Role.class);
    /** Konto lokalne (tryb Mały Urząd): skrót BCrypt; null = logowanie wyłącznie przez WK/PZ lub AD. */
    @Getter(AccessLevel.NONE)
    private String passwordHash;
    private java.time.OffsetDateTime passwordChangedAt;

    public java.util.Optional<String> passwordHash() {
        return java.util.Optional.ofNullable(passwordHash);
    }

    /** Rola główna albo jedna z ról dodatkowych. */
    public boolean hasRole(Role r) {
        return role == r || (secondaryRoles != null && secondaryRoles.contains(r));
    }

    public boolean hasLocalPassword() {
        return passwordHash != null;
    }

    /** Rekordy bez statusu (sprzed V11) — status wyprowadzany z flagi urzędnika SC. */
    public EmploymentStatus getEmploymentStatus() {
        if (employmentStatus != null) {
            return employmentStatus;
        }
        return isCivilServant ? EmploymentStatus.URZEDNIK_SC : EmploymentStatus.PRACOWNIK_SC_NIEOKRESLONY;
    }

    public void setEmploymentStatus(EmploymentStatus status) {
        this.employmentStatus = status;
        this.isCivilServant = status != null && status.isCivilServant();
    }

    public boolean isCivilServant() {
        return getEmploymentStatus().isCivilServant();
    }

    /** Zgodność z flagą {@code civilServant}: true → urzędnik SC; false nie nadpisuje innego statusu pracownika. */
    public void setCivilServant(boolean civilServant) {
        if (civilServant) {
            setEmploymentStatus(EmploymentStatus.URZEDNIK_SC);
        } else if (getEmploymentStatus() == EmploymentStatus.URZEDNIK_SC) {
            setEmploymentStatus(EmploymentStatus.PRACOWNIK_SC_NIEOKRESLONY);
        }
    }
}
