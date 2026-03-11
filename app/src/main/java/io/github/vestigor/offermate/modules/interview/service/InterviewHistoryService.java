package io.github.vestigor.offermate.modules.interview.service;

import io.github.vestigor.offermate.modules.interview.model.dto.InterviewDetailDTO;

public interface InterviewHistoryService {

    /**
     * 获取面试会话详情
     */
    InterviewDetailDTO getInterviewDetail(String sessionId);

    /**
     * 导出面试报告为PDF
     */
    byte[] exportInterviewPdf(String sessionId);
}
