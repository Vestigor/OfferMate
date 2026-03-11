package io.github.vestigor.offermate.modules.interview.model.dto;

import java.util.List;

public record QuestionEvaluationDTO(
        int questionIndex,
        int score,
        String feedback,
        String referenceAnswer,
        List<String> keyPoints
) {}
