package com.riwi.skillbridge.ai;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Constitución art. I: dominio sin frameworks; art. VII: sin dependencia de compilación con backend/. */
class ArchitectureTest {

    private static final Path MAIN = Path.of("src/main/java/com/riwi/skillbridge/ai");
    private static final Pattern FRAMEWORK_IMPORT =
            Pattern.compile("^import (static )?(org\\.springframework|jakarta\\.persistence|org\\.hibernate)\\.", Pattern.MULTILINE);

    @Test
    void domainHasNoFrameworkImports() throws IOException {
        assertThat(offenders(MAIN.resolve("domain"), FRAMEWORK_IMPORT)).isEmpty();
    }

    @Test
    void noImportsFromMonolithPackages() throws IOException {
        // El monolito usa com.riwi.skillbridge.{domain,application,infrastructure}; aquí todo cuelga de ...ai.
        Pattern monolith = Pattern.compile("^import (static )?com\\.riwi\\.skillbridge\\.(?!ai\\.)", Pattern.MULTILINE);
        assertThat(offenders(MAIN, monolith)).isEmpty();
    }

    private static List<Path> offenders(Path root, Pattern pattern) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        try {
                            return pattern.matcher(Files.readString(p)).find();
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .toList();
        }
    }
}
