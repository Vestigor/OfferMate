package io.github.vestigor.offermate.modules.knowledgebase.service;

import org.springframework.ai.document.Document;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface KnowledgeBaseVectorService {

    /**
     * 将知识库内容向量化并存储
     */
    void vectorizeAndStore(Long knowledgeBaseId, String content);

    /**
     * 基于多个知识库进行相似度搜索
     */
    List<Document> similaritySearch(String query, List<Long> knowledgeBaseIds, int topK, double minScore);

    /**
     * 删除指定知识库的所有向量数据
     */
    @Transactional(rollbackFor = Exception.class)
    void deleteByKnowledgeBaseId(Long knowledgeBaseId);
}
