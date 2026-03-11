package io.github.vestigor.offermate.modules.interview.service;

import io.github.vestigor.offermate.modules.interview.model.dto.InterviewQuestionDTO;

import java.util.List;

public interface InterviewQuestionService {

    /**
     * 生成面试问题
     */
    List<InterviewQuestionDTO> generateQuestions(String resumeText, int questionCount, List<String> historicalQuestions);

    /**
     * 生成面试问题（不带历史问题）
     */
    List<InterviewQuestionDTO> generateQuestions(String resumeText, int questionCount);

}