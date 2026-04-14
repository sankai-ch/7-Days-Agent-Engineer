package com.sankai.agent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {com.sankai.agent.Day1Application.class, TestBeans.class})
@AutoConfigureMockMvc
class AskApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void askReturnsGroundedAnswerWithCitations() throws Exception {
        mockMvc.perform(post("/v1/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"MCP 在 Agent 里有什么作用\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grounded").value(true))
                .andExpect(jsonPath("$.citations[0].docId").exists())
                .andExpect(jsonPath("$.answer").exists());
    }

    @Test
    void askValidationError() throws Exception {
        mockMvc.perform(post("/v1/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"\"}"))
                .andExpect(status().isUnprocessableEntity());
    }
}
