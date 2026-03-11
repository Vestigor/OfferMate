package io.github.vestigor.offermate.modules.knowledgebase.service;

import io.github.vestigor.offermate.modules.knowledgebase.model.entity.KnowledgeBaseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface KnowledgeBasePersistenceService {

    /**
     * 处理重复知识库（更新访问计数）
     */
    Map<String, Object> handleDuplicateKnowledgeBase(KnowledgeBaseEntity kb, String fileHash);

    /**
     * 保存新知识库元数据到数据库
     */
    KnowledgeBaseEntity saveKnowledgeBase(MultipartFile file, String name, String category,
                                          String storageKey, String storageUrl, String fileHash);
    /**
     * 更新知识库向量化状态为 PENDING
     */
    void updateVectorStatusToPending(Long kbId);
}
