package com.riwi.skillbridge.application.service.knowledge;

import com.riwi.skillbridge.application.port.out.CategoryRepositoryPort;
import com.riwi.skillbridge.application.port.out.EmbeddingPort;
import com.riwi.skillbridge.application.port.out.KnowledgeIndexPort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.domain.knowledge.KnowledgeChunk;
import com.riwi.skillbridge.domain.model.Category;
import com.riwi.skillbridge.domain.model.MentorshipMode;
import com.riwi.skillbridge.domain.model.Offering;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("KnowledgeIndexService Unit Tests (T022, FR-015, FR-018)")
class KnowledgeIndexServiceTest {

    private KnowledgeCorpusLoader corpusLoader;
    private OfferingRepositoryPort offeringRepository;
    private CategoryRepositoryPort categoryRepository;
    private EmbeddingPort embeddingPort;
    private KnowledgeIndexPort indexPort;
    private KnowledgeIndexService service;

    @BeforeEach
    void setUp() {
        corpusLoader = mock(KnowledgeCorpusLoader.class);
        offeringRepository = mock(OfferingRepositoryPort.class);
        categoryRepository = mock(CategoryRepositoryPort.class);
        embeddingPort = mock(EmbeddingPort.class);
        indexPort = mock(KnowledgeIndexPort.class);

        service = new KnowledgeIndexService(
                corpusLoader,
                offeringRepository,
                categoryRepository,
                embeddingPort,
                indexPort
        );
    }

    private static double[] sampleVector(int dim) {
        double[] vec = new double[dim];
        vec[0] = 1.0;
        return vec;
    }

    @Nested
    @DisplayName("Initial Batch Load & Atomic Replacement (FR-015, FR-018)")
    class InitialLoadTests {

        @Test
        @DisplayName("Carga inicial al arrancar por lotes: corpus .md, ofertas activas y categorías activas reemplaza el índice atómicamente")
        void shouldLoadInitialIndexInBatchAndReplaceIndexAtomically() {
            // Arrange
            KnowledgeChunk policy1 = new KnowledgeChunk(
                    "policy-cancel",
                    KnowledgeChunk.SourceType.POLICY_DOC,
                    "Política de Cancelación",
                    "Texto de la política de cancelación.",
                    KnowledgeChunk.Language.ES,
                    "hash-policy-cancel-123"
            );
            KnowledgeChunk policy2 = new KnowledgeChunk(
                    "policy-faq",
                    KnowledgeChunk.SourceType.POLICY_DOC,
                    "Preguntas Frecuentes",
                    "Texto de preguntas frecuentes.",
                    KnowledgeChunk.Language.ES,
                    "hash-policy-faq-456"
            );
            when(corpusLoader.load()).thenReturn(List.of(policy1, policy2));

            UUID providerId = UUID.randomUUID();
            Offering offering1 = Offering.create(
                    providerId,
                    "Curso de Spring Boot",
                    "Aprende Spring Boot desde cero.",
                    "BACKEND",
                    BigDecimal.valueOf(150),
                    MentorshipMode.LIVE,
                    4
            );
            Offering inactiveOffering = new Offering(
                    UUID.randomUUID(),
                    providerId,
                    "Curso Inactivo",
                    "Descripción inactiva",
                    "BACKEND",
                    BigDecimal.valueOf(100),
                    false,
                    MentorshipMode.ASYNCHRONOUS,
                    1
            );
            when(offeringRepository.findAllActive()).thenReturn(List.of(offering1, inactiveOffering));

            Category category1 = new Category(UUID.randomUUID(), "ARQUITECTURA", true, Instant.now());
            when(categoryRepository.findAllActive()).thenReturn(List.of(category1));

            // 2 políticas + 1 oferta activa (la inactiva se descarta) + 1 categoría = 4 fragmentos
            List<double[]> mockEmbeddings = List.of(
                    sampleVector(768),
                    sampleVector(768),
                    sampleVector(768),
                    sampleVector(768)
            );
            when(embeddingPort.embed(anyList())).thenReturn(mockEmbeddings);

            // Act
            int count = service.loadInitialIndex();

            // Assert
            assertEquals(4, count, "Deben haberse indexado exactamente 4 fragmentos");

            // Verifica que EmbeddingPort se invocó por lotes con una sola llamada para los 4 textos
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<String>> textsCaptor = ArgumentCaptor.forClass(List.class);
            verify(embeddingPort, times(1)).embed(textsCaptor.capture());

            List<String> passedTexts = textsCaptor.getValue();
            assertEquals(4, passedTexts.size());
            assertEquals("Texto de la política de cancelación.", passedTexts.get(0));
            assertEquals("Texto de preguntas frecuentes.", passedTexts.get(1));
            assertEquals("Aprende Spring Boot desde cero.", passedTexts.get(2));
            assertEquals("ARQUITECTURA", passedTexts.get(3));

            // Verifica que KnowledgeIndexPort.replaceIndex se invocó atómicamente con los 4 fragmentos y embeddings
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<KnowledgeChunk>> chunksCaptor = ArgumentCaptor.forClass(List.class);
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<double[]>> embeddingsCaptor = ArgumentCaptor.forClass(List.class);
            verify(indexPort, times(1)).replaceIndex(chunksCaptor.capture(), embeddingsCaptor.capture());

            assertEquals(4, chunksCaptor.getValue().size());
            assertEquals(4, embeddingsCaptor.getValue().size());
            assertEquals("offering:" + offering1.id(), chunksCaptor.getValue().get(2).sourceId());
            assertEquals("category:" + category1.id(), chunksCaptor.getValue().get(3).sourceId());
        }

        @Test
        @DisplayName("Carga inicial con fuentes vacías reemplaza el índice con lista vacía sin invocar embeddings")
        void shouldHandleEmptySourcesGracefully() {
            when(corpusLoader.load()).thenReturn(List.of());
            when(offeringRepository.findAllActive()).thenReturn(List.of());
            when(categoryRepository.findAllActive()).thenReturn(List.of());

            int count = service.loadInitialIndex();

            assertEquals(0, count);
            verify(indexPort).replaceIndex(List.of());
            verifyNoInteractions(embeddingPort);
        }
    }

    @Nested
    @DisplayName("Reindexing & Hash Change Detection (FR-018)")
    class ReindexingHashDetectionTests {

        @Test
        @DisplayName("Reindexado de lote solo recalcula fragmentos cuyo contentHash cambió (FR-018)")
        void shouldRecalculateOnlyChangedHashesInBatch() {
            // Arrange
            String existingHash1 = "hash-unchanged-1";
            String existingHash2 = "hash-unchanged-2";
            when(indexPort.findExistingHashes()).thenReturn(Set.of(existingHash1, existingHash2));

            KnowledgeChunk chunk1 = new KnowledgeChunk(
                    "policy-1",
                    KnowledgeChunk.SourceType.POLICY_DOC,
                    "Política 1",
                    "Contenido 1 sin cambios",
                    KnowledgeChunk.Language.ES,
                    existingHash1
            );
            KnowledgeChunk chunk2 = new KnowledgeChunk(
                    "policy-2",
                    KnowledgeChunk.SourceType.POLICY_DOC,
                    "Política 2",
                    "Contenido 2 sin cambios",
                    KnowledgeChunk.Language.ES,
                    existingHash2
            );
            KnowledgeChunk chunk3Changed = new KnowledgeChunk(
                    "offering:new-123",
                    KnowledgeChunk.SourceType.OFFERING,
                    "Nueva Oferta",
                    "Contenido nuevo o modificado",
                    KnowledgeChunk.Language.ES,
                    "hash-modified-333"
            );

            when(embeddingPort.embed(List.of("Contenido nuevo o modificado")))
                    .thenReturn(List.of(sampleVector(768)));

            // Act
            int recalculatedCount = service.reindexChunks(List.of(chunk1, chunk2, chunk3Changed));

            // Assert: solo 1 fragmento recalculado
            assertEquals(1, recalculatedCount, "Solo el fragmento con hash cambiado debió recalcularse");

            // EmbeddingPort solo recibió el texto cambiado
            verify(embeddingPort, times(1)).embed(List.of("Contenido nuevo o modificado"));

            // Solo se upsertó el fragmento cambiado
            verify(indexPort, times(1)).upsert(chunk3Changed, sampleVector(768));
            verify(indexPort, never()).upsert(org.mockito.ArgumentMatchers.eq(chunk1), any());
            verify(indexPort, never()).upsert(org.mockito.ArgumentMatchers.eq(chunk2), any());
        }

        @Test
        @DisplayName("Reindexado de lote no invoca EmbeddingPort si ningún hash cambió")
        void shouldNotCallEmbeddingPortIfNoHashesChanged() {
            String hashA = "hash-a";
            String hashB = "hash-b";
            when(indexPort.findExistingHashes()).thenReturn(Set.of(hashA, hashB));

            KnowledgeChunk chunkA = new KnowledgeChunk(
                    "doc-a",
                    KnowledgeChunk.SourceType.POLICY_DOC,
                    "Doc A",
                    "Texto A",
                    KnowledgeChunk.Language.ES,
                    hashA
            );
            KnowledgeChunk chunkB = new KnowledgeChunk(
                    "doc-b",
                    KnowledgeChunk.SourceType.CATEGORY,
                    "Doc B",
                    "Texto B",
                    KnowledgeChunk.Language.ES,
                    hashB
            );

            int recalculated = service.reindexChunks(List.of(chunkA, chunkB));

            assertEquals(0, recalculated);
            verifyNoInteractions(embeddingPort);
            verify(indexPort, never()).upsert(any(), any());
        }

        @Test
        @DisplayName("Reindexado de fragmento individual recalcula solo si el hash no existe")
        void shouldReindexSingleChunkOnlyWhenHashDoesNotExist() {
            KnowledgeChunk chunkNew = new KnowledgeChunk(
                    "offering:test-1",
                    KnowledgeChunk.SourceType.OFFERING,
                    "Test",
                    "Texto Test",
                    KnowledgeChunk.Language.ES,
                    "hash-new-123"
            );
            when(indexPort.existsByContentHash("hash-new-123")).thenReturn(false);
            when(embeddingPort.embed("Texto Test")).thenReturn(sampleVector(768));

            boolean updated = service.reindexChunk(chunkNew);

            assertTrue(updated);
            verify(embeddingPort).embed("Texto Test");
            verify(indexPort).upsert(chunkNew, sampleVector(768));
        }

        @Test
        @DisplayName("Reindexado de fragmento individual omite recálculo si el hash ya existe")
        void shouldSkipSingleChunkWhenHashAlreadyExists() {
            KnowledgeChunk chunkExisting = new KnowledgeChunk(
                    "offering:test-2",
                    KnowledgeChunk.SourceType.OFFERING,
                    "Test 2",
                    "Texto Test 2",
                    KnowledgeChunk.Language.ES,
                    "hash-existing-456"
            );
            when(indexPort.existsByContentHash("hash-existing-456")).thenReturn(true);

            boolean updated = service.reindexChunk(chunkExisting);

            assertFalse(updated);
            verifyNoInteractions(embeddingPort);
            verify(indexPort, never()).upsert(any(), any());
        }
    }

    @Nested
    @DisplayName("Offering & Category Synchronization (T023, T024, FR-015)")
    class OfferingCategorySyncTests {

        @Test
        @DisplayName("reindexOffering sincroniza oferta activa recalculando fragmento")
        void shouldReindexActiveOffering() {
            Offering offering = Offering.create(
                    UUID.randomUUID(),
                    "Java Performance Tuning",
                    "Optimización avanzada de JVM y código.",
                    "BACKEND",
                    BigDecimal.valueOf(200)
            );

            when(indexPort.existsByContentHash(anyString())).thenReturn(false);
            when(embeddingPort.embed(anyString())).thenReturn(sampleVector(768));

            boolean result = service.reindexOffering(offering);

            assertTrue(result);
            verify(indexPort).upsert(org.mockito.ArgumentMatchers.argThat(chunk ->
                    chunk.sourceId().equals("offering:" + offering.id()) &&
                    chunk.title().equals("Java Performance Tuning") &&
                    chunk.sourceType() == KnowledgeChunk.SourceType.OFFERING
            ), any());
        }

        @Test
        @DisplayName("reindexOffering omite oferta inactiva (FR-004)")
        void shouldSkipInactiveOffering() {
            Offering inactiveOffering = new Offering(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "Oferta Desactivada",
                    "Desc",
                    "BACKEND",
                    BigDecimal.TEN,
                    false
            );

            boolean result = service.reindexOffering(inactiveOffering);

            assertFalse(result);
            verifyNoInteractions(embeddingPort);
            verify(indexPort, never()).upsert(any(), any());
        }

        @Test
        @DisplayName("reindexCategory sincroniza categoría activa recalculando fragmento")
        void shouldReindexActiveCategory() {
            Category category = new Category(UUID.randomUUID(), "DEVOPS", true, Instant.now());
            when(indexPort.existsByContentHash(anyString())).thenReturn(false);
            when(embeddingPort.embed("DEVOPS")).thenReturn(sampleVector(768));

            boolean result = service.reindexCategory(category);

            assertTrue(result);
            verify(indexPort).upsert(org.mockito.ArgumentMatchers.argThat(chunk ->
                    chunk.sourceId().equals("category:" + category.id()) &&
                    chunk.title().equals("DEVOPS") &&
                    chunk.content().equals("DEVOPS") &&
                    chunk.sourceType() == KnowledgeChunk.SourceType.CATEGORY
            ), any());
        }

        @Test
        @DisplayName("deleteCategory emite registro de notificación")
        void shouldHandleDeleteCategoryNotification() {
            UUID catId = UUID.randomUUID();
            assertDoesNotThrow(() -> service.deleteCategory(catId));
            assertDoesNotThrow(() -> service.onCategoryDeleted(catId));
        }
    }

    @Nested
    @DisplayName("Chunk Mapping & Invariants")
    class ChunkMappingTests {

        @Test
        @DisplayName("toChunk mapea Offering con mode y durationHours correctamente")
        void shouldMapOfferingWithModeAndDuration() {
            UUID providerId = UUID.randomUUID();
            Offering offering = Offering.create(
                    providerId,
                    "Mentoría Docker",
                    "Contenedores desde cero",
                    "DEVOPS",
                    BigDecimal.valueOf(80),
                    MentorshipMode.LIVE,
                    2
            );

            KnowledgeChunk chunk = service.toChunk(offering);

            assertEquals("offering:" + offering.id(), chunk.sourceId());
            assertEquals(KnowledgeChunk.SourceType.OFFERING, chunk.sourceType());
            assertEquals("Mentoría Docker", chunk.title());
            assertEquals("Contenedores desde cero", chunk.content());
            assertEquals(KnowledgeChunk.Language.ES, chunk.language());
            assertEquals("LIVE", chunk.mode());
            assertEquals(2, chunk.durationHours());
            assertNotNull(chunk.contentHash());
            assertEquals(64, chunk.contentHash().length());
            assertTrue(chunk.contentHash().matches("^[0-9a-f]{64}$"));
        }

        @Test
        @DisplayName("toChunk mapea Offering con descripción nula usando el título como contenido")
        void shouldFallbackToTitleWhenOfferingDescriptionIsMissing() {
            Offering offering = new Offering(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "Título Sin Descripción",
                    null,
                    "DEVOPS",
                    BigDecimal.valueOf(50),
                    true
            );

            KnowledgeChunk chunk = service.toChunk(offering);

            assertEquals("Título Sin Descripción", chunk.content());
            assertEquals(KnowledgeIndexService.computeContentHash("Título Sin Descripción"), chunk.contentHash());
        }

        @Test
        @DisplayName("toChunk mapea Category con name como título y contenido")
        void shouldMapCategoryCorrectly() {
            UUID catId = UUID.randomUUID();
            Category category = new Category(catId, "FRONTEND", true, Instant.now());

            KnowledgeChunk chunk = service.toChunk(category);

            assertEquals("category:" + catId, chunk.sourceId());
            assertEquals(KnowledgeChunk.SourceType.CATEGORY, chunk.sourceType());
            assertEquals("FRONTEND", chunk.title());
            assertEquals("FRONTEND", chunk.content());
            assertEquals(KnowledgeChunk.Language.ES, chunk.language());
            assertNull(chunk.mode());
            assertNull(chunk.durationHours());
            assertEquals(64, chunk.contentHash().length());
        }

        @Test
        @DisplayName("Constructor requiere dependencias no nulas")
        void constructorShouldRequireNonNullDependencies() {
            assertThrows(NullPointerException.class, () -> new KnowledgeIndexService(null, offeringRepository, categoryRepository, embeddingPort, indexPort));
            assertThrows(NullPointerException.class, () -> new KnowledgeIndexService(corpusLoader, null, categoryRepository, embeddingPort, indexPort));
            assertThrows(NullPointerException.class, () -> new KnowledgeIndexService(corpusLoader, offeringRepository, null, embeddingPort, indexPort));
            assertThrows(NullPointerException.class, () -> new KnowledgeIndexService(corpusLoader, offeringRepository, categoryRepository, null, indexPort));
            assertThrows(NullPointerException.class, () -> new KnowledgeIndexService(corpusLoader, offeringRepository, categoryRepository, embeddingPort, null));
        }

        @Test
        @DisplayName("KnowledgeIndexService debe ser clase pura sin anotaciones de Spring ni Jakarta")
        void shouldBePureClassWithoutSpringOrJakartaAnnotations() {
            assertEquals(0, KnowledgeIndexService.class.getAnnotations().length,
                    "KnowledgeIndexService no debe tener anotaciones de framework en la clase");
        }
    }
}
