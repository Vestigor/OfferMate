package io.github.vestigor.offermate.modules.resume.model.dto;

import java.time.LocalDateTime;

/**
 * 简历列表项DTO
 */
public record ResumeListItemDTO(
        Long id,
        String filename,
        Long fileSize,
        LocalDateTime uploadedAt,
        Integer accessCount,
        Integer latestScore,
        LocalDateTime lastAnalyzedAt,
        Integer interviewCount
) {}
