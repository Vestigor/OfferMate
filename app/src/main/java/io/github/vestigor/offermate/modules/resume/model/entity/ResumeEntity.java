package io.github.vestigor.offermate.modules.resume.model.entity;

import io.github.vestigor.offermate.common.model.AsyncTaskStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 简历实体
 * Resume Entity for deduplication and persistence
 */
@Data
@Entity
@Table(name = "resumes", indexes = {
        @Index(name = "idx_resume_hash", columnList = "fileHash", unique = true),
        @Index(name = "idx_resume_user_id", columnList = "userId")
})
public class ResumeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 用户ID
    @Column(nullable = false)
    private Long userId;

    // 文件内容的SHA-256哈希值，用于去重
    @Column(nullable = false, unique = true, length = 64)
    private String fileHash;

    // 原始文件名
    @Column(nullable = false)
    private String originalFilename;

    // 文件大小（字节）
    private Long fileSize;

    // 文件类型
    private String contentType;

    // RustFS存储的文件Key
    @Column(length = 500)
    private String storageKey;

    // RustFS存储的文件URL
    @Column(length = 1000)
    private String storageUrl;

    // 解析后的简历文本
    @Column(columnDefinition = "TEXT")
    private String resumeText;

    // 上传时间
    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    // 最后访问时间
    private LocalDateTime lastAccessedAt;

    // 访问次数
    private Integer accessCount = 0;

    // 分析状态
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AsyncTaskStatus analyzeStatus = AsyncTaskStatus.PENDING;

    // 分析错误信息（失败时记录）
    @Column(length = 500)
    private String analyzeError;

    @PrePersist
    protected void onCreate() {
        uploadedAt = LocalDateTime.now();
        lastAccessedAt = LocalDateTime.now();
        accessCount = 1;
    }

    public void incrementAccessCount() {
        this.accessCount++;
        this.lastAccessedAt = LocalDateTime.now();
    }
}
