package com.sankai.agent;

import com.sankai.agent.client.LlmClient;
import com.sankai.agent.exception.ExtractionException;
import com.sankai.agent.model.ExtractResult;
import com.sankai.agent.service.ExtractionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExtractionServiceTest {

    @Test
    void extractSuccessOnFirstTry() {
        LlmClient client = new SequenceClient(List.of(
                "{\"intent\":\"task\",\"priority\":\"high\",\"summary\":\"Fix prod issue\",\"tags\":[\"prod\"],\"needsFollowUp\":true,\"confidence\":0.9}"
        ));

        ExtractionService service = new ExtractionService(client, new ObjectMapper(), validator());
        ExtractResult result = service.extract("Please fix this ASAP");

        assertEquals(ExtractResult.Intent.task, result.getIntent());
        assertEquals(ExtractResult.Priority.high, result.getPriority());
    }

    @Test
    void extractRetriesThenSuccess() {
        SequenceClient client = new SequenceClient(List.of(
                "{bad json",
                "{\"intent\":\"question\"}",
                "{\"intent\":\"question\",\"priority\":\"medium\",\"summary\":\"Need docs\",\"tags\":[\"docs\"],\"needsFollowUp\":true,\"confidence\":0.8}"
        ));

        ExtractionService service = new ExtractionService(client, new ObjectMapper(), validator());
        ExtractResult result = service.extract("How does this work?");

        assertEquals(ExtractResult.Intent.question, result.getIntent());
        assertEquals(3, client.calls);
    }

    @Test
    void extractFailsAfterMaxRetries() {
        LlmClient client = new SequenceClient(List.of("{}", "{}", "{}"));
        ExtractionService service = new ExtractionService(client, new ObjectMapper(), validator());

        assertThrows(ExtractionException.class, () -> service.extract("bad input"));
    }

    private Validator validator() {
        return Validation.buildDefaultValidatorFactory().getValidator();
    }

    static class SequenceClient implements LlmClient {
        private final List<String> outputs;
        private int calls = 0;

        SequenceClient(List<String> outputs) {
            this.outputs = outputs;
        }

        @Override
        public String complete(String prompt) {
            String out = outputs.get(calls);
            calls += 1;
            return out;
        }
    }
}
