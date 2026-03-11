package io.github.vestigor.offermate.modules.knowledgebase.service;

import io.github.vestigor.offermate.modules.knowledgebase.model.dto.KnowledgeBaseListItemDTO;
import io.github.vestigor.offermate.modules.knowledgebase.model.dto.KnowledgeBaseStatsDTO;
import io.github.vestigor.offermate.modules.knowledgebase.model.entity.KnowledgeBaseEntity;
import io.github.vestigor.offermate.modules.knowledgebase.model.status.VectorStatus;

import java.util.List;
import java.util.Optional;

public interface KnowledgeBaseListService {

    /**
     * 获取知识库列表（支持状态过滤和排序）
     */
    List<KnowledgeBaseListItemDTO> listKnowledgeBases(VectorStatus vectorStatus, String sortBy);

    /**
     * 获取所有知识库列表
     */
    List<KnowledgeBaseListItemDTO> listKnowledgeBases();

    /**
     * 按向量化状态获取知识库列表
     */
    List<KnowledgeBaseListItemDTO> listKnowledgeBasesByStatus(VectorStatus vectorStatus);

    /**
     * 根据ID获取知识库详情
     */
    Optional<KnowledgeBaseListItemDTO> getKnowledgeBase(Long id);

    /**
     * 根据ID获取知识库实体
     */
     Optional<KnowledgeBaseEntity> getKnowledgeBaseEntity(Long id);

    /**
     * 根据ID列表获取知识库名称列表
     */
    List<String> getKnowledgeBaseNames(List<Long> ids);

    // ========== 分类管理 ==========

    /**
     * 获取所有分类
     */
    List<String> getAllCategories();

    /**
     * 根据分类获取知识库列表
     */
    List<KnowledgeBaseListItemDTO> listByCategory(String category);

    /**
     * 更新知识库分类
     */
    void updateCategory(Long id, String category);

    // ========== 搜索功能 ==========

    /**
     * 按关键词搜索知识库
     */
    List<KnowledgeBaseListItemDTO> search(String keyword);

    // ========== 排序功能 ==========

    /**
     * 按指定字段排序获取知识库列表（保持向后兼容）
     */
    List<KnowledgeBaseListItemDTO> listSorted(String sortBy);

    // ========== 统计功能 ==========

    /**
     * 获取知识库统计信息
     * 总提问次数从用户消息数统计，确保多知识库提问只算一次
     */
    KnowledgeBaseStatsDTO getStatistics();

    // ========== 下载功能 ==========

    /**
     * 下载知识库文件
     */
    byte[] downloadFile(Long id);

    /**
     * 获取知识库文件信息（用于下载）
     */
    KnowledgeBaseEntity getEntityForDownload(Long id);
}
