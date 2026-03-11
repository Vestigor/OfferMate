package io.github.vestigor.offermate.modules.resume.model.dto;

public record ScoreDetailDTO(
        int contentScore,
        int structureScore,
        int skillMatchScore,
        int expressionScore,
        int projectScore
) {}
