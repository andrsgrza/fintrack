package com.fintrack.app.config;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * A deterministic clock for an explicitly isolated e2e application only.
 *
 * <p>Normal development and production profiles continue to receive the system UTC clock from {@link TimeConfiguration}.
 */
@Configuration
@Profile("e2e")
public class E2eFixedClockConfiguration {

    @Bean
    @Primary
    public Clock e2eFixedClock(@Value("${fintrack.e2e.fixed-clock}") String fixedClock) {
        return Clock.fixed(Instant.parse(fixedClock), ZoneOffset.UTC);
    }
}
