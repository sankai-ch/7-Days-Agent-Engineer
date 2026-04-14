package com.sankai.agent.service;

import com.sankai.agent.model.Citation;
import com.sankai.agent.repository.SegmentRepository;
import dev.langchain4j.community.model.dashscope.QwenEmbeddingModel;
import dev.langchain4j.data.segment.TextSegment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RetrievalService {

    private final QwenEmbeddingModel embeddingModel;
    private final SegmentRepository segmentRepository;

    @Value("${rag.retrieval.top-k:3}")
    private int topK;

    public RetrievalService(QwenEmbeddingModel embeddingModel, SegmentRepository segmentRepository) {
        this.embeddingModel = embeddingModel;
        this.segmentRepository = segmentRepository;
    }

    public List<Citation> retrieveTop(String question) {
        float[] queryVec = embeddingModel.embed(TextSegment.from(question)).content().vector();
        return segmentRepository.findTopK(queryVec, topK).stream()
                .map(row -> {
                    String docId = extractDocId(row.metadata());
                    String snippet = row.content().length() > 220
                            ? row.content().substring(0, 220)
                            : row.content();
                    return new Citation(docId, snippet.replace("\n", " "), row.score());
                })
                .toList();
    }

    private String extractDocId(String metadataJson) {
        if (metadataJson == null) return "unknown";
        int start = metadataJson.indexOf("\"docId\":\"");
        if (start < 0) return "unknown";
        start += 9;
        int end = metadataJson.indexOf('"', start);
        return end > start ? metadataJson.substring(start, end) : "unknown";
    }
}
