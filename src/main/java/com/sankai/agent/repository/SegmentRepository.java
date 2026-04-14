package com.sankai.agent.repository;

import com.sankai.agent.entity.KnowledgeSegment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface SegmentRepository extends JpaRepository<KnowledgeSegment, UUID> {

    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO knowledge_segments (id, content, metadata, embedding)
            VALUES (gen_random_uuid(), :content, CAST(:metadata AS jsonb), CAST(:embedding AS vector))
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    void upsertNative(@Param("content") String content,
                      @Param("metadata") String metadata,
                      @Param("embedding") String embedding);

    @Query(value = """
            SELECT content, metadata::text,
                   1 - (embedding <=> CAST(:embedding AS vector)) AS score
            FROM knowledge_segments
            ORDER BY embedding <=> CAST(:embedding AS vector)
            LIMIT :topK
            """, nativeQuery = true)
    List<Object[]> findTopKRaw(@Param("embedding") String embedding, @Param("topK") int topK);

    default void upsert(String content, Map<String, Object> metadata, float[] embedding) {
        upsertNative(content, toJson(metadata), toVectorLiteral(embedding));
    }

    default List<SegmentRow> findTopK(float[] queryEmbedding, int topK) {
        return findTopKRaw(toVectorLiteral(queryEmbedding), topK).stream()
                .map(row -> new SegmentRow((String) row[0], (String) row[1], ((Number) row[2]).doubleValue()))
                .toList();
    }

    private static String toVectorLiteral(float[] v) {
        var sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }

    private static String toJson(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) return "{}";
        var sb = new StringBuilder("{");
        metadata.forEach((k, v) -> sb.append('"').append(k).append("\":\"").append(v).append("\","));
        sb.setCharAt(sb.length() - 1, '}');
        return sb.toString();
    }

    record SegmentRow(String content, String metadata, double score) {}
}
