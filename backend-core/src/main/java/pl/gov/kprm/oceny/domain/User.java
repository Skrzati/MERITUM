package pl.gov.kprm.oceny.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
}
