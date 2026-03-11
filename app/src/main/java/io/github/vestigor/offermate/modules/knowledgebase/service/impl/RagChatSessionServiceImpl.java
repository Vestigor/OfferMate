package io.github.vestigor.offermate.modules.knowledgebase.service.impl;

import io.github.vestigor.offermate.common.exception.BusinessException;
import io.github.vestigor.offermate.common.exception.ErrorCode;
import io.github.vestigor.offermate.infrastructure.mapper.KnowledgeBaseMapper;
import io.github.vestigor.offermate.infrastructure.mapper.RagChatMapper;
import io.github.vestigor.offermate.modules.knowledgebase.model.entity.KnowledgeBaseEntity;
import io.github.vestigor.offermate.modules.knowledgebase.model.entity.RagChatMessageEntity;
import io.github.vestigor.offermate.modules.knowledgebase.model.entity.RagChatSessionEntity;
import io.github.vestigor.offermate.modules.knowledgebase.model.dto.KnowledgeBaseListItemDTO;
import io.github.vestigor.offermate.modules.knowledgebase.model.dto.RagChatDTO;
import io.github.vestigor.offermate.modules.knowledgebase.repository.KnowledgeBaseRepository;
import io.github.vestigor.offermate.modules.knowledgebase.repository.RagChatMessageRepository;
import io.github.vestigor.offermate.modules.knowledgebase.repository.RagChatSessionRepository;
import io.github.vestigor.offermate.modules.knowledgebase.service.KnowledgeBaseQueryService;
import io.github.vestigor.offermate.modules.knowledgebase.service.RagChatSessionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.util.HashSet;
import java.util.List;

/**
 * RAG 聊天会话服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagChatSessionServiceImpl implements RagChatSessionService {

    private final RagChatSessionRepository sessionRepository;
    private final RagChatMessageRepository messageRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeBaseQueryService queryService;
    private final RagChatMapper ragChatMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;

    /**
     * 创建新会话
     */
    @Override
    @Transactional
    public RagChatDTO.SessionDTO createSession(RagChatDTO.CreateSessionRequest request) {
        // 验证知识库存在
        List<KnowledgeBaseEntity> knowledgeBases = knowledgeBaseRepository
                .findAllById(request.knowledgeBaseIds());

        if (knowledgeBases.size() != request.knowledgeBaseIds().size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "部分知识库不存在");
        }

        // 创建会话
        RagChatSessionEntity session = new RagChatSessionEntity();
        session.setTitle(request.title() != null && !request.title().isBlank()
                ? request.title()
                : generateTitle(knowledgeBases));
        session.setKnowledgeBases(new HashSet<>(knowledgeBases));

        session = sessionRepository.save(session);

        log.info("创建 RAG 聊天会话: id={}, title={}", session.getId(), session.getTitle());

        return ragChatMapper.toSessionDTO(session);
    }

    /**
     * 获取会话列表
     */
    @Override
    public List<RagChatDTO.SessionListItemDTO> listSessions() {
        return sessionRepository.findAllOrderByPinnedAndUpdatedAtDesc()
                .stream()
                .map(ragChatMapper::toSessionListItemDTO)
                .toList();
    }

    /**
     * 获取会话详情（包含消息）
     * 分两次查询避免笛卡尔积问题
     */
    @Override
    public RagChatDTO.SessionDetailDTO getSessionDetail(Long sessionId) {
        // 先加载会话和知识库
        RagChatSessionEntity session = sessionRepository
                .findByIdWithKnowledgeBases(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "会话不存在"));

        // 再单独加载消息（避免笛卡尔积）
        List<RagChatMessageEntity> messages = messageRepository
                .findBySessionIdOrderByMessageOrderAsc(sessionId);

        // 转换知识库列表
        List<KnowledgeBaseListItemDTO> kbDTOs = knowledgeBaseMapper.toListItemDTOList(
                new java.util.ArrayList<>(session.getKnowledgeBases())
        );

        return ragChatMapper.toSessionDetailDTO(session, messages, kbDTOs);
    }

    /**
     * 准备流式消息
     */
    @Override
    @Transactional
    public Long prepareStreamMessage(Long sessionId, String question) {
        RagChatSessionEntity session = sessionRepository.findByIdWithKnowledgeBases(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "会话不存在"));

        // 获取当前消息数量作为起始顺序
        int nextOrder = session.getMessageCount();

        // 保存用户消息
        RagChatMessageEntity userMessage = new RagChatMessageEntity();
        userMessage.setSession(session);
        userMessage.setType(RagChatMessageEntity.MessageType.USER);
        userMessage.setContent(question);
        userMessage.setMessageOrder(nextOrder);
        userMessage.setCompleted(true);
        messageRepository.save(userMessage);

        // 创建 AI 消息占位（未完成）
        RagChatMessageEntity assistantMessage = new RagChatMessageEntity();
        assistantMessage.setSession(session);
        assistantMessage.setType(RagChatMessageEntity.MessageType.ASSISTANT);
        assistantMessage.setContent("");
        assistantMessage.setMessageOrder(nextOrder + 1);
        assistantMessage.setCompleted(false);
        assistantMessage = messageRepository.save(assistantMessage);

        // 更新会话消息数量
        session.setMessageCount(nextOrder + 2);
        sessionRepository.save(session);

        log.info("准备流式消息: sessionId={}, messageId={}", sessionId, assistantMessage.getId());

        return assistantMessage.getId();
    }

    /**
     * 流式响应完成后更新消息
     */
    @Override
    @Transactional
    public void completeStreamMessage(Long messageId, String content) {
        RagChatMessageEntity message = messageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "消息不存在"));

        message.setContent(content);
        message.setCompleted(true);
        messageRepository.save(message);

        log.info("完成流式消息: messageId={}, contentLength={}", messageId, content.length());
    }

    /**
     * 获取流式回答
     */
    @Override
    public Flux<String> getStreamAnswer(Long sessionId, String question) {
        RagChatSessionEntity session = sessionRepository.findByIdWithKnowledgeBases(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "会话不存在"));

        List<Long> kbIds = session.getKnowledgeBaseIds();

        return queryService.answerQuestionStream(kbIds, question);
    }

    /**
     * 更新会话标题
     */
    @Override
    @Transactional
    public void updateSessionTitle(Long sessionId, String title) {
        RagChatSessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "会话不存在"));

        session.setTitle(title);
        sessionRepository.save(session);

        log.info("更新会话标题: sessionId={}, title={}", sessionId, title);
    }

    /**
     * 切换会话置顶状态
     */
    @Override
    @Transactional
    public void togglePin(Long sessionId) {
        RagChatSessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "会话不存在"));

        // 处理 null 值（兼容旧数据）
        Boolean currentPinned = session.getIsPinned() != null ? session.getIsPinned() : false;
        session.setIsPinned(!currentPinned);
        sessionRepository.save(session);

        log.info("切换会话置顶状态: sessionId={}, isPinned={}", sessionId, session.getIsPinned());
    }

    /**
     * 更新会话的知识库关联
     */
    @Override
    @Transactional
    public void updateSessionKnowledgeBases(Long sessionId, List<Long> knowledgeBaseIds) {
        RagChatSessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "会话不存在"));

        List<KnowledgeBaseEntity> knowledgeBases = knowledgeBaseRepository
                .findAllById(knowledgeBaseIds);

        session.setKnowledgeBases(new HashSet<>(knowledgeBases));
        sessionRepository.save(session);

        log.info("更新会话知识库: sessionId={}, kbIds={}", sessionId, knowledgeBaseIds);
    }

    /**
     * 删除会话
     */
    @Override
    @Transactional
    public void deleteSession(Long sessionId) {
        if (!sessionRepository.existsById(sessionId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "会话不存在");
        }
        sessionRepository.deleteById(sessionId);

        log.info("删除会话: sessionId={}", sessionId);
    }

    private String generateTitle(List<KnowledgeBaseEntity> knowledgeBases) {
        if (knowledgeBases.isEmpty()) {
            return "新对话";
        }
        if (knowledgeBases.size() == 1) {
            return knowledgeBases.getFirst().getName();
        }
        return knowledgeBases.size() + " 个知识库对话";
    }
}
