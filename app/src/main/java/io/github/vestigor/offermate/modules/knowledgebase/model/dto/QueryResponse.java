package io.github.vestigor.offermate.modules.knowledgebase.model.dto;

/**
 * 知识库查询响应
 */
public record QueryResponse(
        String answer,
        Long knowledgeBaseId,
        String knowledgeBaseName
) {}
