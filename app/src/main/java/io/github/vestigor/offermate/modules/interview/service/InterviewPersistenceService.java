package io.github.vestigor.offermate.modules.interview.service;

import io.github.vestigor.offermate.common.model.AsyncTaskStatus;
import io.github.vestigor.offermate.modules.interview.model.dto.InterviewQuestionDTO;
import io.github.vestigor.offermate.modules.interview.model.dto.InterviewReportDTO;
import io.github.vestigor.offermate.modules.interview.model.entity.InterviewAnswerEntity;
import io.github.vestigor.offermate.modules.interview.model.entity.InterviewSessionEntity;

import java.util.List;
import java.util.Optional;

public interface InterviewPersistenceService {

    /**
     * 保存新的面试会话
     */
    InterviewSessionEntity saveSession(String sessionId, Long resumeId, int totalQuestions, List<InterviewQuestionDTO> questions);

    /**
     * 保存题目生成中的面试会话（异步生成模式，状态为GENERATING）
     */
    InterviewSessionEntity saveSessionForGenerate(String sessionId, Long resumeId, int requestedQuestionCount);

    /**
     * 题目生成完成后更新会话（保存题目、更新状态为CREATED）
     */
    void updateSessionAfterGenerate(String sessionId, List<InterviewQuestionDTO> questions, int followUpCount);

    /**
     * 设置题目生成错误
     */
    void setGenerateError(String sessionId, String error);

    /**
     * 查找指定状态的最新面试会话
     */
    Optional<InterviewSessionEntity> findFirstByResumeIdAndStatus(Long resumeId, InterviewSessionEntity.SessionStatus status);

    /**
     * 更新会话状态
     */
    void updateSessionStatus(String sessionId, InterviewSessionEntity.SessionStatus status);

    /**
     * 更新评估状态
     */
    void updateEvaluateStatus(String sessionId, AsyncTaskStatus status, String error);

    /**
     * 更新当前问题索引
     */
    void updateCurrentQuestionIndex(String sessionId, int index);

    /**
     * 保存面试答案
     */
    InterviewAnswerEntity saveAnswer(String sessionId, int questionIndex, String question, String category, String userAnswer, int score, String feedback);

    /**
     * 保存面试报告
     */
    void saveReport(String sessionId, InterviewReportDTO report);

    /**
     * 根据会话ID获取会话
     */
    Optional<InterviewSessionEntity> findBySessionId(String sessionId);


    /**
     * 获取简历的所有面试记录
     */
    List<InterviewSessionEntity> findByResumeId(Long resumeId);

    /**
     * 删除简历的所有面试会话
     */
    void deleteSessionsByResumeId(Long resumeId);

    /**
     * 删除单个面试会话
     */
    void deleteSessionBySessionId(String sessionId);

    /**
     * 查找未完成的面试会话（CREATED或IN_PROGRESS状态）
     */
    Optional<InterviewSessionEntity> findUnfinishedSession(Long resumeId);

    /**
     * 根据会话ID查找所有答案
     */
    List<InterviewAnswerEntity> findAnswersBySessionId(String sessionId);

    /**
     * 获取简历的历史提问列表（限制最近的 N 条）
     */
    List<String> getHistoricalQuestionsByResumeId(Long resumeId);
}