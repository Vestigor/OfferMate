package io.github.vestigor.offermate.modules.knowledgebase.repository;

import io.github.vestigor.offermate.modules.knowledgebase.model.entity.RagChatSessionEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RagChatSessionRepository extends JpaRepository<RagChatSessionEntity, Long> {

    /**
     * 获取用户所有会话（置顶在前，按更新时间倒序）
     */
    @Query("SELECT s FROM RagChatSessionEntity s WHERE s.userId = :userId ORDER BY s.isPinned DESC, s.updatedAt DESC")
    List<RagChatSessionEntity> findByUserIdOrderByIsPinnedDescUpdatedAtDesc(@Param("userId") Long userId);

    /**
     * 获取用户指定会话（含知识库）
     */
    @Query("SELECT s FROM RagChatSessionEntity s LEFT JOIN FETCH s.knowledgeBases WHERE s.id = :id AND s.userId = :userId")
    Optional<RagChatSessionEntity> findByIdAndUserIdWithKnowledgeBases(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 获取用户指定会话（不含知识库）
     */
    Optional<RagChatSessionEntity> findByIdAndUserId(Long id, Long userId);

    /**
     * 获取用户所有会话（用于账号注销级联清理）
     */
    List<RagChatSessionEntity> findByUserId(Long userId);

    /**
     * 根据知识库ID查找相关会话（用于删除知识库时清理关联）
     */
    @Query("SELECT DISTINCT s FROM RagChatSessionEntity s JOIN s.knowledgeBases kb WHERE kb.id IN :kbIds ORDER BY s.updatedAt DESC")
    List<RagChatSessionEntity> findByKnowledgeBaseIds(@Param("kbIds") List<Long> knowledgeBaseIds);

}
