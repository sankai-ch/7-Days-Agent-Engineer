package com.sankai.agent.service;

import dev.langchain4j.community.model.dashscope.QwenEmbeddingModel;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import io.github.resilience4j.retry.annotation.Retry;
import com.sankai.agent.repository.SegmentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class KnowledgeBaseService {

    private static final int CHUNK_SIZE = 500;
    private static final int OVERLAP = 50;

    private final QwenEmbeddingModel embeddingModel;
    private final SegmentRepository segmentRepository;
    private final ExecutorService executor = Executors.newFixedThreadPool(8);

    @Value("${rag.knowledge.path:}")
    private String knowledgePath;

    @Value("${rag.knowledge.classpath-pattern:classpath:knowledge/*.md}")
    private String classpathPattern;

    public KnowledgeBaseService(QwenEmbeddingModel embeddingModel, SegmentRepository segmentRepository) {
        this.embeddingModel = embeddingModel;
        this.segmentRepository = segmentRepository;
    }

//    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        syncClasspath();
        if (knowledgePath != null && !knowledgePath.isBlank()) {
            syncDirectory(Path.of(knowledgePath));
        }
    }

    private void syncClasspath() {
        try {
            var resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(classpathPattern);
            for (var r : resources) {
                var content = new String(r.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                var docId = r.getFilename() != null ? r.getFilename() : "unknown";
                embedAndStore(docId, content);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sync classpath knowledge", e);
        }
    }

    private void syncDirectory(Path dir) {
        try (var stream = Files.walk(dir)) {
            stream.filter(p -> p.toString().endsWith(".md"))
                  .forEach(p -> {
                      try {
                          embedAndStore(p.getFileName().toString(), Files.readString(p));
                      } catch (Exception e) {
                          throw new IllegalStateException("Failed to process " + p, e);
                      }
                  });
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sync directory " + dir, e);
        }
    }

    private void embedAndStore(String docId, String content) {
        var chunks = chunk(content);
        var futures = chunks.stream()
                .map(chunk -> CompletableFuture.runAsync(() -> {
                    var embedding = embed(chunk);
                    segmentRepository.upsert(chunk, Map.of("docId", docId), embedding);
                }, executor))
                .toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    @Retry(name = "embedding")
    private float[] embed(String text) {
        Embedding embedding = embeddingModel.embed(TextSegment.from(text)).content();
        return embedding.vector();
    }

    private List<String> chunk(String text) {
        var chunks = new ArrayList<String>();
        text = text.trim();
        int len = text.length();
        int start = 0;
        while (start < len) {
            int end = Math.min(start + CHUNK_SIZE, len);
            chunks.add(text.substring(start, end));
            start += CHUNK_SIZE - OVERLAP;
        }
        return chunks;
    }
}
