package io.github.vestigor.offermate.modules.knowledgebase.service.impl;

import io.github.vestigor.offermate.common.exception.BusinessException;
import io.github.vestigor.offermate.common.exception.ErrorCode;
import io.github.vestigor.offermate.common.security.SecurityUtils;
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

    @Override
    @Transactional
    public RagChatDTO.SessionDTO createSession(RagChatDTO.CreateSessionRequest request) {
        List<KnowledgeBaseEntity> knowledgeBases = knowledgeBaseRepository
                .findAllById(request.knowledgeBaseIds());

        if (knowledgeBases.size() != request.knowledgeBaseIds().size()) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND, "部分知识库不存在");
        }

        RagChatSessionEntity session = new RagChatSessionEntity();
        session.setUserId(SecurityUtils.getUserId());
        session.setTitle(request.title() != null && !request.title().isBlank()
                ? request.title()
                : generateTitle(knowledgeBases));
        session.setKnowledgeBases(new HashSet<>(knowledgeBases));
        session = sessionRepository.save(session);

        log.info("创建 RAG 聊天会话: id={}, title={}", session.getId(), session.getTitle());

        return ragChatMapper.toSessionDTO(session);
    }

    @Override
    public List<RagChatDTO.SessionListItemDTO> listSessions() {
        return sessionRepository.findByUserIdOrderByIsPinnedDescUpdatedAtDesc(SecurityUtils.getUserId())
                .stream()
                .map(ragChatMapper::toSessionListItemDTO)
                .toList();
    }

    @Override
    public RagChatDTO.SessionDetailDTO getSessionDetail(Long sessionId) {
        RagChatSessionEntity session = sessionRepository
                .findByIdAndUserIdWithKnowledgeBases(sessionId, SecurityUtils.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RAG_CHAT_SESSION_NOT_FOUND, "会话不存在"));

        List<RagChatMessageEntity> messages = messageRepository
                .findBySessionIdOrderByMessageOrderAsc(sessionId);

        List<KnowledgeBaseListItemDTO> kbDTOs = knowledgeBaseMapper.toListItemDTOList(
                new java.util.ArrayList<>(session.getKnowledgeBases())
        );

        return ragChatMapper.toSessionDetailDTO(session, messages, kbDTOs);
    }

    @Override
    @Transactional
    public Long prepareStreamMessage(Long sessionId, String question) {
        RagChatSessionEntity session = sessionRepository
                .findByIdAndUserIdWithKnowledgeBases(sessionId, SecurityUtils.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RAG_CHAT_SESSION_NOT_FOUND, "会话不存在"));

        int nextOrder = session.getMessageCount();

        RagChatMessageEntity userMessage = new RagChatMessageEntity();
        userMessage.setSession(session);
        userMessage.setType(RagChatMessageEntity.MessageType.USER);
        userMessage.setContent(question);
        userMessage.setMessageOrder(nextOrder);
        userMessage.setCompleted(true);
        messageRepository.save(userMessage);

        RagChatMessageEntity assistantMessage = new RagChatMessageEntity();
        assistantMessage.setSession(session);
        assistantMessage.setType(RagChatMessageEntity.MessageType.ASSISTANT);
        assistantMessage.setContent("");
        assistantMessage.setMessageOrder(nextOrder + 1);
        assistantMessage.setCompleted(false);
        assistantMessage = messageRepository.save(assistantMessage);

        session.setMessageCount(nextOrder + 2);
        sessionRepository.save(session);

        log.info("准备流式消息: sessionId={}, messageId={}", sessionId, assistantMessage.getId());

        return assistantMessage.getId();
    }

    @Override
    @Transactional
    public void completeStreamMessage(Long messageId, String content) {
        RagChatMessageEntity message = messageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RAG_CHAT_MESSAGE_NOT_FOUND, "消息不存在"));

        message.setContent(content);
        message.setCompleted(true);
        messageRepository.save(message);

        log.info("完成流式消息: messageId={}, contentLength={}", messageId, content.length());
    }

    @Override
    public Flux<String> getStreamAnswer(Long sessionId, String question) {
        RagChatSessionEntity session = sessionRepository
                .findByIdAndUserIdWithKnowledgeBases(sessionId, SecurityUtils.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RAG_CHAT_SESSION_NOT_FOUND, "会话不存在"));

        return queryService.answerQuestionStream(session.getKnowledgeBaseIds(), question);
    }

    @Override
    @Transactional
    public void updateSessionTitle(Long sessionId, String title) {
        RagChatSessionEntity session = sessionRepository
                .findByIdAndUserId(sessionId, SecurityUtils.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RAG_CHAT_SESSION_NOT_FOUND, "会话不存在"));

        session.setTitle(title);
        sessionRepository.save(session);

        log.info("更新会话标题: sessionId={}, title={}", sessionId, title);
    }

    @Override
    @Transactional
    public void togglePin(Long sessionId) {
        RagChatSessionEntity session = sessionRepository
                .findByIdAndUserId(sessionId, SecurityUtils.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RAG_CHAT_SESSION_NOT_FOUND, "会话不存在"));

        session.setIsPinned(!session.getIsPinned());
        sessionRepository.save(session);

        log.info("切换会话置顶状态: sessionId={}, isPinned={}", sessionId, session.getIsPinned());
    }

    @Override
    @Transactional
    public void updateSessionKnowledgeBases(Long sessionId, List<Long> knowledgeBaseIds) {
        RagChatSessionEntity session = sessionRepository
                .findByIdAndUserId(sessionId, SecurityUtils.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RAG_CHAT_SESSION_NOT_FOUND, "会话不存在"));

        List<KnowledgeBaseEntity> knowledgeBases = knowledgeBaseRepository.findAllById(knowledgeBaseIds);
        session.setKnowledgeBases(new HashSet<>(knowledgeBases));
        sessionRepository.save(session);

        log.info("更新会话知识库: sessionId={}, kbIds={}", sessionId, knowledgeBaseIds);
    }

    @Override
    @Transactional
    public void deleteSession(Long sessionId) {
        RagChatSessionEntity session = sessionRepository
                .findByIdAndUserId(sessionId, SecurityUtils.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RAG_CHAT_SESSION_NOT_FOUND, "会话不存在"));

        sessionRepository.delete(session);

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
