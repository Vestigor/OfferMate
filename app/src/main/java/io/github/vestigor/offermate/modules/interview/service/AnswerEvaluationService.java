package io.github.vestigor.offermate.modules.interview.service;

import io.github.vestigor.offermate.modules.interview.model.dto.InterviewQuestionDTO;
import io.github.vestigor.offermate.modules.interview.model.dto.InterviewReportDTO;

import java.util.List;

public interface AnswerEvaluationService {

    /**
     * 评估完整面试并生成报告
     */
    InterviewReportDTO evaluateInterview(String sessionId, String resumeText, List<InterviewQuestionDTO> questions);
}
