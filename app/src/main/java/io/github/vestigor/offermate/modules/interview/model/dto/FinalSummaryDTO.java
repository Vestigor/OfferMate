package io.github.vestigor.offermate.modules.interview.model.dto;

import java.util.List;

public record FinalSummaryDTO(
        String overallFeedback,
        List<String> strengths,
        List<String> improvements
) {}
