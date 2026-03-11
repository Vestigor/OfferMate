package io.github.vestigor.offermate.modules.knowledgebase.service;

import io.github.vestigor.offermate.modules.knowledgebase.model.dto.RagChatDTO;
import reactor.core.publisher.Flux;

import java.util.List;

public interface RagChatSessionService {

    /**
     * 创建新会话
     */
    RagChatDTO.SessionDTO createSession(RagChatDTO.CreateSessionRequest request);

    /**
     * 获取会话列表
     */
    List<RagChatDTO.SessionListItemDTO> listSessions();

    /**
     * 获取会话详情
     */
    RagChatDTO.SessionDetailDTO getSessionDetail(Long sessionId);

    /**
     * 准备流式消息（保存用户消息，创建 AI 消息占位）
     */
    Long prepareStreamMessage(Long sessionId, String question);

    /**
     * 流式响应完成后更新消息
     */
    void completeStreamMessage(Long messageId, String content);

    /**
     * 获取流式回答
     */
    Flux<String> getStreamAnswer(Long sessionId, String question);

    /**
     * 更新会话标题
     */
    void updateSessionTitle(Long sessionId, String title);

    /**
     * 切换会话置顶状态
     */
    void togglePin(Long sessionId);

    /**
     * 更新会话的知识库关联
     */
    void updateSessionKnowledgeBases(Long sessionId, List<Long> knowledgeBaseIds);

    /**
     * 删除会话
     */
    void deleteSession(Long sessionId);
}
