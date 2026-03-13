package io.github.vestigor.offermate.modules.knowledgebase.controller;

import io.github.vestigor.offermate.common.result.Result;
import io.github.vestigor.offermate.modules.knowledgebase.model.dto.RagChatDTO;
import io.github.vestigor.offermate.modules.knowledgebase.service.RagChatSessionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * RAG 聊天控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/rag-chat")
@RequiredArgsConstructor
public class RagChatController {

    private final RagChatSessionService sessionService;

    /**
     * 创建新会话
     */
    @PostMapping("/sessions")
    public Result<RagChatDTO.SessionDTO> createSession(@Valid @RequestBody RagChatDTO.CreateSessionRequest request) {
        return Result.success(sessionService.createSession(request));
    }

    /**
     * 获取会话列表
     */
    @GetMapping("/sessions")
    public Result<List<RagChatDTO.SessionListItemDTO>> listSessions() {
        return Result.success(sessionService.listSessions());
    }

    /**
     * 获取会话详情（包含消息历史）
     */
    @GetMapping("/sessions/{sessionId}")
    public Result<RagChatDTO.SessionDetailDTO> getSessionDetail(@PathVariable Long sessionId) {
        return Result.success(sessionService.getSessionDetail(sessionId));
    }

    /**
     * 更新会话标题
     */
    @PutMapping("/sessions/{sessionId}/title")
    public Result<Void> updateSessionTitle(
            @PathVariable Long sessionId,
            @Valid @RequestBody RagChatDTO.UpdateTitleRequest request) {
        sessionService.updateSessionTitle(sessionId, request.title());
        return Result.success(null);
    }

    /**
     * 切换会话置顶状态
     */
    @PutMapping("/sessions/{sessionId}/pin")
    public Result<Void> togglePin(@PathVariable Long sessionId) {
        sessionService.togglePin(sessionId);
        return Result.success(null);
    }

    /**
     * 更新会话知识库
     */
    @PutMapping("/sessions/{sessionId}/knowledge-bases")
    public Result<Void> updateSessionKnowledgeBases(
            @PathVariable Long sessionId,
            @Valid @RequestBody RagChatDTO.UpdateKnowledgeBasesRequest request) {
        sessionService.updateSessionKnowledgeBases(sessionId, request.knowledgeBaseIds());
        return Result.success(null);
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> deleteSession(@PathVariable Long sessionId) {
        sessionService.deleteSession(sessionId);
        return Result.success(null);
    }

    /**
     * 发送消息（流式SSE）
     * 流式响应设计：
     * 1. 先同步保存用户消息和创建 AI 消息占位
     * 2. 返回流式响应
     * 3. 流式完成后通过回调更新消息
     */
    @PostMapping(value = "/sessions/{sessionId}/messages/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> sendMessageStream(
            @PathVariable Long sessionId,
            @Valid @RequestBody RagChatDTO.SendMessageRequest request) {

        log.info("收到 RAG 聊天流式请求: sessionId={}, question={}, 线程: {} (虚拟线程: {})",
                sessionId, request.question(), Thread.currentThread(), Thread.currentThread().isVirtual());

        Long messageId = sessionService.prepareStreamMessage(sessionId, request.question());

        StringBuilder fullContent = new StringBuilder();

        return sessionService.getStreamAnswer(sessionId, request.question())
                .doOnNext(fullContent::append)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk.replace("\n", "\\n").replace("\r", "\\r"))
                        .build())
                .doOnComplete(() -> {
                    sessionService.completeStreamMessage(messageId, fullContent.toString());
                    log.info("RAG 聊天流式完成: sessionId={}, messageId={}", sessionId, messageId);
                })
                .doOnError(e -> {
                    String content = !fullContent.isEmpty()
                            ? fullContent.toString()
                            : "【错误】回答生成失败：" + e.getMessage();
                    sessionService.completeStreamMessage(messageId, content);
                    log.error("RAG 聊天流式错误: sessionId={}", sessionId, e);
                });
    }

}
