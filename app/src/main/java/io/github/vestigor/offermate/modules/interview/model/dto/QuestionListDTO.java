package io.github.vestigor.offermate.modules.interview.model.dto;

import java.util.List;

public record QuestionListDTO(
        List<QuestionDTO> questions
) {}
