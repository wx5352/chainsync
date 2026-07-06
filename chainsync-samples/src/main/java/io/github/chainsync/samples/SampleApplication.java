package io.github.chainsync.samples;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot application demonstrating chainsync.
 *
 * <p>Run it with:
 * <pre>mvn -pl chainsync-samples -am spring-boot:run</pre>
 *
 * All synchronization behavior is driven by {@code application.yml}; the only application code that
 * touches chain data is {@link TransferWatchHandler}.
 */
@SpringBootApplication
public class SampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(SampleApplication.class, args);
    }
}
