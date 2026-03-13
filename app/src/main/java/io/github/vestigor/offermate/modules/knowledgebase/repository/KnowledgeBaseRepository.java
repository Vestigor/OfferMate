package io.github.vestigor.offermate.modules.knowledgebase.repository;

import io.github.vestigor.offermate.modules.knowledgebase.model.entity.KnowledgeBaseEntity;
import io.github.vestigor.offermate.modules.knowledgebase.model.status.VectorStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBaseEntity, Long> {

    // ==================== 去重查询 ====================

    Optional<KnowledgeBaseEntity> findByUserIdAndFileHash(Long userId, String fileHash);

    boolean existsByUserIdAndFileHash(Long userId, String fileHash);

    // ==================== 用户维度查询 ====================

    List<KnowledgeBaseEntity> findByUserIdOrderByUploadedAtDesc(Long userId);

    List<KnowledgeBaseEntity> findByUserId(Long userId);

    @Modifying
    void deleteByUserId(Long userId);

    // ==================== 状态过滤 ====================

    List<KnowledgeBaseEntity> findByUserIdAndVectorStatusOrderByUploadedAtDesc(Long userId, VectorStatus vectorStatus);

    // ==================== 分类查询 ====================

    List<KnowledgeBaseEntity> findByUserIdAndCategoryOrderByUploadedAtDesc(Long userId, String category);

    List<KnowledgeBaseEntity> findByUserIdAndCategoryIsNullOrderByUploadedAtDesc(Long userId);

    @Query("SELECT DISTINCT k.category FROM KnowledgeBaseEntity k WHERE k.userId = :userId AND k.category IS NOT NULL ORDER BY k.category")
    List<String> findCategoriesByUserId(@Param("userId") Long userId);

    // ==================== 搜索 ====================

    @Query("SELECT k FROM KnowledgeBaseEntity k WHERE k.userId = :userId AND (LOWER(k.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(k.originalFilename) LIKE LOWER(CONCAT('%', :keyword, '%'))) ORDER BY k.uploadedAt DESC")
    List<KnowledgeBaseEntity> searchByUserIdAndKeyword(@Param("userId") Long userId, @Param("keyword") String keyword);

    // ==================== 批量更新 ====================

    @Modifying
    @Query("UPDATE KnowledgeBaseEntity k SET k.questionCount = k.questionCount + 1 WHERE k.id IN :ids")
    int incrementQuestionCountBatch(@Param("ids") List<Long> ids);

    // ==================== 统计查询 ====================

    long countByUserId(Long userId);

    long countByUserIdAndVectorStatus(Long userId, VectorStatus vectorStatus);

    @Query("SELECT COALESCE(SUM(k.accessCount), 0) FROM KnowledgeBaseEntity k WHERE k.userId = :userId")
    long sumAccessCountByUserId(@Param("userId") Long userId);
}
