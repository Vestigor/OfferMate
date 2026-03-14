package io.github.vestigor.offermate.modules.interview.model.dto;

import java.util.List;
/**
 * 面试会话DTO
 */
public record InterviewSessionDTO(
        String sessionId,
        String resumeText,
        int totalQuestions,
        int currentQuestionIndex,
        List<InterviewQuestionDTO> questions,
        SessionStatus status,
        String generateError,
        Integer followUpCount
) {
    public enum SessionStatus {
        GENERATING,   // 题目生成中（异步生成）
        CREATED,      // 会话已创建（题目已就绪）
        IN_PROGRESS,  // 面试进行中
        COMPLETED,    // 面试已完成
        EVALUATED     // 已生成评估报告
    }
}
