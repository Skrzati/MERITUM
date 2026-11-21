package pl.gov.kprm.oceny.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class AppConfig {
    public static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");

    @Bean
    public Clock clock() {
        return Clock.system(ZONE);
    }
}
