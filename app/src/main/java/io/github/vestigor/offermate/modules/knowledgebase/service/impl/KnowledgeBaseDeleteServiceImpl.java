package io.github.vestigor.offermate.modules.knowledgebase.service.impl;

import io.github.vestigor.offermate.common.exception.BusinessException;
import io.github.vestigor.offermate.common.exception.ErrorCode;
import io.github.vestigor.offermate.infrastructure.file.FileStorageService;
import io.github.vestigor.offermate.modules.knowledgebase.model.entity.KnowledgeBaseEntity;
import io.github.vestigor.offermate.modules.knowledgebase.model.entity.RagChatSessionEntity;
import io.github.vestigor.offermate.modules.knowledgebase.repository.KnowledgeBaseRepository;
import io.github.vestigor.offermate.modules.knowledgebase.repository.RagChatSessionRepository;
import io.github.vestigor.offermate.modules.knowledgebase.service.KnowledgeBaseDeleteService;
import io.github.vestigor.offermate.modules.knowledgebase.service.KnowledgeBaseVectorService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
/**
 * 知识库删除服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseDeleteServiceImpl implements KnowledgeBaseDeleteService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final RagChatSessionRepository sessionRepository;
    private final KnowledgeBaseVectorService vectorService;
    private final FileStorageService storageService;

    /**
     * 删除知识库
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteKnowledgeBase(Long id) {
        // 获取知识库信息
        KnowledgeBaseEntity kb = knowledgeBaseRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND, "知识库不存在"));

        // 删除所有RAG会话中的知识库关联（必须先删除关联，否则外键约束会阻止删除）
        List<RagChatSessionEntity> sessions = sessionRepository.findByKnowledgeBaseIds(List.of(id));
        for (RagChatSessionEntity session : sessions) {
            session.getKnowledgeBases().removeIf(kbEntity -> kbEntity.getId().equals(id));
            sessionRepository.save(session);
            log.debug("已从会话中移除知识库关联: sessionId={}, kbId={}", session.getId(), id);
        }
        if (!sessions.isEmpty()) {
            log.info("已从 {} 个会话中移除知识库关联: kbId={}", sessions.size(), id);
        }

        // 删除向量数据
        try {
            vectorService.deleteByKnowledgeBaseId(id);
        } catch (Exception e) {
            log.warn("删除向量数据失败，继续删除知识库: kbId={}, error={}", id, e.getMessage());
        }

        // 删除RustFS中的文件
        try {
            storageService.deleteKnowledgeBase(kb.getStorageKey());
        } catch (Exception e) {
            log.warn("删除RustFS文件失败，继续删除知识库记录: kbId={}, error={}", id, e.getMessage());
        }

        // 删除知识库记录
        knowledgeBaseRepository.deleteById(id);
        log.info("知识库已删除: id={}", id);
    }

}
