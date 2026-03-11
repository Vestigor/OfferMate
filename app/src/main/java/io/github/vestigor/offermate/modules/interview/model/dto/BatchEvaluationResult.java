package io.github.vestigor.offermate.modules.interview.model.dto;

public record BatchEvaluationResult(
        int startIndex,
        int endIndex,
        EvaluationReportDTO report
) {}
