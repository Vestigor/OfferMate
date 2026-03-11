package io.github.vestigor.offermate.modules.knowledgebase.service;

import io.github.vestigor.offermate.modules.knowledgebase.model.dto.QueryRequest;
import io.github.vestigor.offermate.modules.knowledgebase.model.dto.QueryResponse;

import reactor.core.publisher.Flux;

import java.util.List;

public interface KnowledgeBaseQueryService {
    /**
     * 基于单个知识库回答用户问题
     */
    String answerQuestion(Long knowledgeBaseId, String question);

    /**
     * 基于多个知识库回答用户问题（RAG）
     */
    String answerQuestion(List<Long> knowledgeBaseIds, String question);

    /**
     * 查询知识库并返回完整响应
     */
    QueryResponse queryKnowledgeBase(QueryRequest request);

    /**
     * 流式查询知识库（SSE）
     */
    Flux<String> answerQuestionStream(List<Long> knowledgeBaseIds, String question);
}
