package io.github.vestigor.offermate.modules.resume.repository;

import io.github.vestigor.offermate.modules.resume.model.entity.ResumeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 简历Repository
 */
@Repository
public interface ResumeRepository extends JpaRepository<ResumeEntity, Long> {

    /**
     * 根据文件哈希查找简历
     */
    Optional<ResumeEntity> findByUserIdAndFileHash(Long userId, String fileHash);

    /**
     * 检查文件哈希是否存在
     */
    boolean existsByUserIdAndFileHash(Long userId, String fileHash);

    /**
     * 根据用户ID查找所有简历
     */
    List<ResumeEntity> findByUserId(Long userId);
}
