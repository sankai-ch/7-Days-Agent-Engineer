package com.sankai.agent.service;

import com.sankai.agent.model.AskResponse;
import com.sankai.agent.model.Citation;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RagService {

    private static final String SYSTEM_TEMPLATE =
            "你是一个技术专家。请根据提供的背景信息回答问题。如果信息不足，请回答不知道。回答末尾必须标明引用来源。背景信息：%s";

    private final RetrievalService retrievalService;
    private final QwenChatModel chatModel;

    public RagService(RetrievalService retrievalService, QwenChatModel chatModel) {
        this.retrievalService = retrievalService;
        this.chatModel = chatModel;
    }

    public AskResponse ask(String question) {
        List<Citation> citations = retrievalService.retrieveTop(question);

        if (citations.isEmpty()) {
            return new AskResponse("我在当前知识库里没有找到足够依据。请补充更具体的问题或先扩展知识库文档。", false, List.of());
        }

        String context = citations.stream()
                .map(c -> "[" + c.getDocId() + "] " + c.getSnippet())
                .collect(Collectors.joining("\n"));

        String answer = chatModel.chat(
                List.of(SystemMessage.from(SYSTEM_TEMPLATE.formatted(context)),
                        UserMessage.from(question))).aiMessage().text();

        return new AskResponse(answer, true, citations);
    }
}
