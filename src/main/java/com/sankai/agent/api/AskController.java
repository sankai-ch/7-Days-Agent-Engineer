package com.sankai.agent.api;

import com.sankai.agent.model.AskRequest;
import com.sankai.agent.model.AskResponse;
import com.sankai.agent.service.RagService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 处理提问相关的 REST 控制器。
 */
@RestController
public class AskController {
    private final RagService ragService;

    /**
     * 构造函数，注入 RagService。
     *
     * @param ragService RAG 服务实例
     */
    public AskController(RagService ragService) {
        this.ragService = ragService;
    }

    /**
     * 处理提问请求的端点。
     *
     * @param request 包含问题的请求对象
     * @return 提问的响应对象
     */
    @PostMapping("/v1/ask")
    public AskResponse ask(@Valid @RequestBody AskRequest request) {
        return ragService.ask(request.getQuestion());
    }
}
