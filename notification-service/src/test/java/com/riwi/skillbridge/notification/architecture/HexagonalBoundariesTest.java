package com.riwi.skillbridge.notification.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reglas de la constitución (Art. I y VII) comprobadas sobre el código fuente:
 * dominio y aplicación sin frameworks, sin imports del monolito y sin controladores HTTP.
 */
class HexagonalBoundariesTest {

    private static final Path MAIN = Path.of("src/main/java/com/riwi/skillbridge/notification");
    private static final List<String> FRAMEWORK_PREFIXES = List.of(
        "import org.springframework.", "import jakarta.", "import javax.persistence.",
        "import com.fasterxml.", "import org.apache.kafka.", "import org.slf4j.");

    @Test
    void domainAndApplicationHaveNoFrameworkImports() throws IOException {
        assertThat(importsMatching(MAIN.resolve("domain"), FRAMEWORK_PREFIXES)).isEmpty();
        assertThat(importsMatching(MAIN.resolve("application"), FRAMEWORK_PREFIXES)).isEmpty();
    }

    @Test
    void noCompileDependencyOnMonolith() throws IOException {
        try (Stream<Path> files = javaFiles(MAIN)) {
            List<String> offending = files.flatMap(HexagonalBoundariesTest::lines)
                .filter(line -> line.startsWith("import com.riwi.skillbridge.")
                    && !line.startsWith("import com.riwi.skillbridge.notification."))
                .toList();
            assertThat(offending).isEmpty();
        }
        assertThat(Files.readString(Path.of("pom.xml"))).doesNotContain("<artifactId>skillbridge");
    }

    @Test
    void noControllers() throws IOException {
        try (Stream<Path> files = javaFiles(MAIN)) {
            assertThat(files.flatMap(HexagonalBoundariesTest::lines)
                .filter(line -> line.contains("@RestController") || line.contains("@Controller")
                    || line.contains("@RequestMapping") || line.contains("@GetMapping")
                    || line.contains("@PostMapping"))
                .toList()).isEmpty();
        }
    }

    private static List<String> importsMatching(Path dir, List<String> prefixes) throws IOException {
        try (Stream<Path> files = javaFiles(dir)) {
            return files.flatMap(file -> lines(file).map(line -> file.getFileName() + ": " + line))
                .filter(entry -> prefixes.stream().anyMatch(p -> entry.substring(entry.indexOf(": ") + 2).startsWith(p)))
                .toList();
        }
    }

    private static Stream<Path> javaFiles(Path dir) throws IOException {
        return Files.walk(dir).filter(p -> p.toString().endsWith(".java"));
    }

    private static Stream<String> lines(Path file) {
        try {
            return Files.readAllLines(file).stream().map(String::trim);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
