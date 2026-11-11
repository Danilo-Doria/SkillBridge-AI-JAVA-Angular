package com.riwi.skillbridge.infrastructure.adapter.out.persistence.knowledge;

import com.riwi.skillbridge.application.port.out.EmbeddingPort;
import com.riwi.skillbridge.application.port.out.KnowledgeCandidate;
import com.riwi.skillbridge.application.port.out.KnowledgeIndexPort;
import com.riwi.skillbridge.application.port.out.KnowledgeSearchPort;
import com.riwi.skillbridge.domain.knowledge.KnowledgeChunk;
import com.riwi.skillbridge.domain.knowledge.KnowledgeQuery;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Adaptador de persistencia pgvector sobre el schema knowledge.
 * Implementa KnowledgeSearchPort y KnowledgeIndexPort para búsqueda por similitud coseno
 * y mantenimiento atómico del índice de fragmentos de conocimiento.
 */
@Component
public class PgVectorKnowledgeAdapter implements KnowledgeSearchPort, KnowledgeIndexPort {

    private final JpaKnowledgeChunkRepository repository;
    private final EmbeddingPort embeddingPort;
    private final EntityManager entityManager;

    public PgVectorKnowledgeAdapter(
            JpaKnowledgeChunkRepository repository,
            EmbeddingPort embeddingPort,
            EntityManager entityManager
    ) {
        this.repository = Objects.requireNonNull(repository, "repository no puede ser nulo");
        this.embeddingPort = Objects.requireNonNull(embeddingPort, "embeddingPort no puede ser nulo");
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager no puede ser nulo");
    }

    @Override
    public List<KnowledgeCandidate> search(String query, int topK) {
        if (query == null || query.isBlank() || topK <= 0) {
            return List.of();
        }

        double[] queryEmbedding = embeddingPort.embed(query);
        if (queryEmbedding == null || queryEmbedding.length == 0) {
            return List.of();
        }

        String vectorLiteral = toVectorString(queryEmbedding);
        List<KnowledgeChunkSearchResult> rows = repository.searchNearest(vectorLiteral, topK);

        return rows.stream()
                .map(row -> {
                    KnowledgeChunk chunk = toDomain(row);
                    double similarity = row.getSimilarity() != null
                            ? row.getSimilarity()
                            : (1.0 - (row.getDistance() != null ? row.getDistance() : 0.0));
                    return new KnowledgeCandidate(chunk, similarity);
                })
                .toList();
    }

    @Override
    public List<KnowledgeCandidate> search(KnowledgeQuery query, int topK) {
        if (query == null) {
            throw new IllegalArgumentException("query no puede ser nula");
        }
        return search(query.question(), topK);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> findExistingHashes() {
        Set<String> hashes = repository.findAllContentHashes();
        return hashes != null ? hashes : Set.of();
    }

    @Override
    @Transactional
    public void upsert(KnowledgeChunk chunk, double[] embedding) {
        Objects.requireNonNull(chunk, "chunk no puede ser nulo");
        Objects.requireNonNull(embedding, "embedding no puede ser nulo");

        Optional<KnowledgeChunkEntity> existing = repository.findBySourceId(chunk.sourceId());

        if (existing.isPresent()) {
            KnowledgeChunkEntity entity = existing.get();
            entity.setSourceId(chunk.sourceId());
            entity.setSourceType(chunk.sourceType().name());
            entity.setTitle(chunk.title());
            entity.setContent(chunk.content());
            entity.setLanguage(chunk.language().name());
            entity.setContentHash(chunk.contentHash());
            entity.setMode(chunk.mode());
            entity.setDurationHours(chunk.durationHours());
            entity.setEmbedding(toVectorString(embedding));
            entity.setUpdatedAt(Instant.now());
            repository.save(entity);
        } else {
            KnowledgeChunkEntity entity = toEntity(chunk, embedding);
            entityManager.persist(entity);
        }
    }

    @Override
    @Transactional
    public void replaceIndex(List<IndexedChunk> chunks) {
        Objects.requireNonNull(chunks, "chunks no puede ser nulo");

        repository.deleteAllChunks();

        if (!chunks.isEmpty()) {
            for (IndexedChunk ic : chunks) {
                KnowledgeChunkEntity entity = toEntity(ic.chunk(), ic.embedding());
                entityManager.persist(entity);
            }
        }

        entityManager.flush();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByContentHash(String contentHash) {
        if (contentHash == null || contentHash.isBlank()) {
            return false;
        }
        return repository.existsByContentHash(contentHash);
    }

    // Conversión proyección ↔ dominio exclusiva dentro del adaptador (Constitución Art. I.3)

    private KnowledgeChunk toDomain(KnowledgeChunkSearchResult row) {
        return new KnowledgeChunk(
                row.getSourceId(),
                KnowledgeChunk.SourceType.valueOf(row.getSourceType()),
                row.getTitle(),
                row.getContent(),
                KnowledgeChunk.Language.valueOf(row.getLanguage()),
                row.getContentHash(),
                row.getMode(),
                row.getDurationHours()
        );
    }

    private KnowledgeChunkEntity toEntity(KnowledgeChunk chunk, double[] embedding) {
        return new KnowledgeChunkEntity(
                UUID.randomUUID(),
                chunk.sourceId(),
                chunk.sourceType().name(),
                chunk.title(),
                chunk.content(),
                chunk.language().name(),
                chunk.contentHash(),
                chunk.mode(),
                chunk.durationHours(),
                toVectorString(embedding),
                Instant.now()
        );
    }

    private static String toVectorString(double[] vector) {
        if (vector == null) {
            throw new IllegalArgumentException("El vector de embedding no puede ser nulo");
        }
        StringBuilder sb = new StringBuilder(vector.length * 12);
        sb.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}
