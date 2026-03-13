package io.github.vestigor.offermate.modules.knowledgebase.service.impl;

import io.github.vestigor.offermate.common.exception.BusinessException;
import io.github.vestigor.offermate.common.exception.ErrorCode;
import io.github.vestigor.offermate.modules.knowledgebase.model.entity.KnowledgeBaseEntity;
import io.github.vestigor.offermate.modules.knowledgebase.repository.KnowledgeBaseRepository;
import io.github.vestigor.offermate.modules.knowledgebase.service.KnowledgeBaseCountService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 知识库计数服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseCountServiceImpl implements KnowledgeBaseCountService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;

    /**
     * 批量更新知识库提问计数
     * 每个知识库的 questionCount + 1，表示该知识库参与回答的次数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateQuestionCounts(List<Long> knowledgeBaseIds) {
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            return;
        }

        List<Long> uniqueIds = knowledgeBaseIds.stream().distinct().toList();

        Set<Long> existingIds = new HashSet<>(knowledgeBaseRepository.findAllById(uniqueIds)
                .stream().map(KnowledgeBaseEntity::getId).toList());

        for (Long id : uniqueIds) {
            if (!existingIds.contains(id)){
                throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND, "知识库不存在: " + id);
            }
        }

        int updated = knowledgeBaseRepository.incrementQuestionCountBatch(uniqueIds);
        log.info("批量更新知识库提问计数: ids = {}, updated = {}", uniqueIds, updated);
    }
}

