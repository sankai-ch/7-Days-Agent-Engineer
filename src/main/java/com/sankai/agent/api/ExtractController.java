package com.sankai.agent.api;

import com.sankai.agent.model.ExtractRequest;
import com.sankai.agent.model.ExtractResult;
import com.sankai.agent.service.ExtractionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 控制器类，处理结构化数据提取相关的 API 请求。
 */
@RestController
public class ExtractController {
    private final ExtractionService extractionService;

    /**
     * 构造函数，注入 ExtractionService。
     *
     * @param extractionService 用于处理提取逻辑的服务
     */
    public ExtractController(ExtractionService extractionService) {
        this.extractionService = extractionService;
    }

    /**
     * 健康检查接口，用于确认服务是否正常运行。
     *
     * @return 包含状态信息的 Map
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    /**
     * 提取接口，接收用户文本并从中提取结构化数据。
     *
     * @param request 包含用户文本的提取请求对象
     * @return 提取出的结构化结果
     */
    @PostMapping("/v1/extract")
    @ResponseStatus(HttpStatus.OK)
    public ExtractResult extract(@Valid @RequestBody ExtractRequest request) {
        return extractionService.extract(request.getUserText());
    }
}
