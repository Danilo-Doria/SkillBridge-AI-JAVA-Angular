package com.riwi.skillbridge.payment;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Reglas hexagonales (constitución Art. I) y aislamiento respecto de backend/ verificadas sobre el código fuente. */
class ArchitectureTest {

    private static final Path ROOT = Path.of("src/main/java/com/riwi/skillbridge/payment");

    private static List<Path> javaFiles(Path dir) throws IOException {
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    @Test
    void domainIsPureJava() throws IOException {
        for (Path file : javaFiles(ROOT.resolve("domain"))) {
            String source = Files.readString(file);
            assertThat(source).as(file.toString())
                    .doesNotContain("import org.springframework", "import jakarta.", "import com.fasterxml",
                            "import org.apache.kafka", "import com.stripe");
        }
    }

    @Test
    void applicationDoesNotDependOnInfrastructure() throws IOException {
        for (Path file : javaFiles(ROOT.resolve("application"))) {
            assertThat(Files.readString(file)).as(file.toString())
                    .doesNotContain("payment.infrastructure", "import com.stripe", "import org.apache.kafka",
                            "import org.springframework.jdbc", "import org.springframework.kafka");
        }
    }

    @Test
    void noCompileDependencyOnTheMonolith() throws IOException {
        for (Path file : javaFiles(ROOT)) {
            assertThat(Files.readString(file)).as(file.toString())
                    .doesNotContainPattern("import com\\.riwi\\.skillbridge\\.(domain|application|infrastructure)\\.");
        }
        assertThat(Files.readString(Path.of("pom.xml"))).doesNotContain("<artifactId>skillbridge");
    }
}
