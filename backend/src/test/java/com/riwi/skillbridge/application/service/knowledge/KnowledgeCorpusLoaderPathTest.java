package com.riwi.skillbridge.application.service.knowledge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("KnowledgeCorpusLoader path segment rules (FR-004)")
class KnowledgeCorpusLoaderPathTest {

    private static final String PUBLIC_DOC = """
            ---
            sourceId: cancellation-policy
            type: POLICY_DOC
            title: Cancelación / Cancellation
            visibility: public
            ---
            ## Español
            Contenido en español.
            ## English
            English content.
            """;

    private final KnowledgeCorpusLoader loader = new KnowledgeCorpusLoader();

    @Test
    @DisplayName("Un directorio como mi-proyecto-docs no descarta el corpus")
    void projectPathContainingDocsInsideAWordIsStillLoaded() throws IOException {
        Resource resource = resourceAt("file:/home/dev/mi-proyecto-docs/backend/target/classes/knowledge/cancellation-policy.md");

        assertEquals(2, loader.loadFromResources(resource).size());
    }

    @Test
    @DisplayName("Un documento bajo un segmento docs real se descarta")
    void documentUnderRealDocsSegmentIsDiscarded() throws IOException {
        Resource resource = resourceAt("file:/home/dev/proyecto/docs/knowledge/cancellation-policy.md");

        assertTrue(loader.loadFromResources(resource).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"classpath*:knowledge/*.md", "classpath:knowledge/cancellation-policy.md"})
    @DisplayName("Los patrones con knowledge/ como segmento se aceptan")
    void knowledgePatternsAreAccepted(String pattern) {
        assertFalse(KnowledgeCorpusLoader.hasPathSegment(pattern, "docs"));
        assertTrue(KnowledgeCorpusLoader.hasPathSegment(pattern, "knowledge"));
        assertTrue(new KnowledgeCorpusLoader().loadFromPattern(pattern) != null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"classpath*:docs/*.md", "file:/x/docs/y.md"})
    @DisplayName("Los patrones con docs como segmento se rechazan")
    void docsPatternsAreRejected(String pattern) {
        assertThrows(IllegalArgumentException.class, () -> loader.loadFromPattern(pattern));
    }

    private static Resource resourceAt(String uri) {
        return new ByteArrayResource(PUBLIC_DOC.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public URI getURI() {
                return URI.create(uri);
            }

            @Override
            public String getFilename() {
                return "cancellation-policy.md";
            }
        };
    }
}
