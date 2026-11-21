package pl.gov.kprm.oceny.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Wstrzykuje bieżącego użytkownika (z nagłówka X-Demo-User-Id) do parametru kontrolera. */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}
