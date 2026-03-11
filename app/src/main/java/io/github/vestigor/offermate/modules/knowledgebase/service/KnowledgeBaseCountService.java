package io.github.vestigor.offermate.modules.knowledgebase.service;

import java.util.List;

public interface KnowledgeBaseCountService {

    /**
     * 批量更新知识库提问计数（使用单条 SQL 批量更新）
     * 每个知识库的 questionCount +1，表示该知识库参与回答的次数
     */
    void updateQuestionCounts(List<Long> knowledgeBaseIds);

}
