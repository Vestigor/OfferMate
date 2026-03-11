package io.github.vestigor.offermate.modules.interview.model.dto;

/**
 * 提交答案响应
 */
public record SubmitAnswerResponse(
        boolean hasNextQuestion,
        InterviewQuestionDTO nextQuestion,
        int currentIndex,
        int totalQuestions
) {}
