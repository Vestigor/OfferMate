package io.github.vestigor.offermate.modules.interview.model.dto;

import java.util.List;

public record QuestionDTO(
        String question,
        String type,
        String category,
        List<String> followUps
) {}
