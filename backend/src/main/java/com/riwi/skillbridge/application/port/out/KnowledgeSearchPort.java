package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.knowledge.KnowledgeChunk;
import com.riwi.skillbridge.domain.knowledge.KnowledgeQuery;
import java.util.List;

/**
 * Puerto de salida para la búsqueda híbrida de conocimiento (BM25 + embeddings fusionados con RRF).
 */
public interface KnowledgeSearchPort {

    /**
     * Realiza una búsqueda híbrida y devuelve los fragmentos candidatos ordenados
     * junto con su puntuación de similitud coseno.
     *
     * @param query texto de la consulta
     * @param topK  número máximo de candidatos a recuperar
     * @return lista de candidatos con su similitud coseno
     */
    List<KnowledgeCandidate> search(String query, int topK);

    /**
     * Sobrecarga que acepta un objeto de dominio KnowledgeQuery.
     *
     * @param query objeto de consulta
     * @param topK  número máximo de candidatos a recuperar
     * @return lista de candidatos con su similitud coseno
     */
    default List<KnowledgeCandidate> search(KnowledgeQuery query, int topK) {
        if (query == null) {
            throw new IllegalArgumentException("query no puede ser nula");
        }
        return search(query.question(), topK);
    }
}
