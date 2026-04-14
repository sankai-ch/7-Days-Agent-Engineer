package com.sankai.agent.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "knowledge_segments")
public class KnowledgeSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSONB")
    private Map<String, Object> metadata;

    @Column(columnDefinition = "TEXT")
    private String embedding;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public KnowledgeSegment() {}

    public KnowledgeSegment(String content, Map<String, Object> metadata, String embedding) {
        this.content = content;
        this.metadata = metadata;
        this.embedding = embedding;
    }

    public UUID getId() { return id; }
    public String getContent() { return content; }
    public Map<String, Object> getMetadata() { return metadata; }
    public String getEmbedding() { return embedding; }
}
