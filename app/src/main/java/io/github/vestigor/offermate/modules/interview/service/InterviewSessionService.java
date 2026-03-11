package io.github.vestigor.offermate.modules.interview.service;

import io.github.vestigor.offermate.modules.interview.model.dto.*;

import java.util.Map;
import java.util.Optional;

public interface InterviewSessionService {

    /**
     * 创建新的面试会话
     * 如果已有未完成的会话，不会创建新的，而是返回现有会话
     * 前端应该先调用 findUnfinishedSession 检查，或者使用 forceCreate 参数强制创建
     */
    InterviewSessionDTO createSession(CreateInterviewRequest request);

    /**
     * 获取会话信息
     */
    InterviewSessionDTO getSession(String sessionId);

    /**
     * 查找并恢复未完成的面试会话
     */
    Optional<InterviewSessionDTO> findUnfinishedSession(Long resumeId);

    /**
     * 查找并恢复未完成的面试会话，如果不存在则抛出异常
     */
    InterviewSessionDTO findUnfinishedSessionOrThrow(Long resumeId);

    /**
     * 获取当前问题的响应（包含完成状态）
     */
    Map<String, Object> getCurrentQuestionResponse(String sessionId);

    /**
     * 获取当前问题
     */
    InterviewQuestionDTO getCurrentQuestion(String sessionId);

    /**
     * 提交答案，并进入下一题；如果是最后一题，自动触发异步评估
     */
    SubmitAnswerResponse submitAnswer(SubmitAnswerRequest request);

    /**
     * 暂存答案
     */
    void saveAnswer(SubmitAnswerRequest request);

    /**
     * 提前交卷,触发异步评估
     */
    void completeInterview(String sessionId);

    /**
     * 生成评估报告
     */
    InterviewReportDTO generateReport(String sessionId);
}
