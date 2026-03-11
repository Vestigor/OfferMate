package io.github.vestigor.offermate.modules.knowledgebase.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 向量存储Repository
 * 负责向量数据的增删改查操作
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class VectorRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 删除指定知识库的所有向量数据
     * 使用 SQL 直接删除，利用数据库索引和删除能力
     */
    @Transactional(rollbackFor = Exception.class)
    public int deleteByKnowledgeBaseId(Long knowledgeBaseId) {
        log.info("开始删除知识库向量数据: kbId={}", knowledgeBaseId);

        /*
         * 注意：
         * metadata 字段是 json 类型，不支持 jsonb_exists 函数。
         * 使用 metadata->>'key' IS NOT NULL 来替代键存在性检查，这在 json/jsonb 下都有效。
         */
        String sql = """
            DELETE FROM vector_store
            WHERE metadata->>'kb_id' = ?
               OR (metadata->>'kb_id_long' IS NOT NULL AND (metadata->>'kb_id_long')::bigint = ?)
            """;

        try {
            // 第一个参数转为 String 匹配 kb_id，第二个参数保持 Long 匹配 kb_id_long
            int deletedRows = jdbcTemplate.update(sql, knowledgeBaseId.toString(), knowledgeBaseId);

            if (deletedRows > 0) {
                log.info("成功删除知识库向量数据: kbId={}, 删除行数={}", knowledgeBaseId, deletedRows);
            } else {
                log.info("未找到相关向量数据，无需删除: kbId={}", knowledgeBaseId);
            }

            return deletedRows;

        } catch (Exception e) {
            log.error("执行删除向量 SQL 失败: kbId={}, error={}", knowledgeBaseId, e.getMessage());
            // 抛出异常以触发事务回滚
            throw new RuntimeException("删除向量数据失败", e);
        }
    }
}
