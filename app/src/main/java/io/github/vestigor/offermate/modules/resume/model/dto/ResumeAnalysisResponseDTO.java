package io.github.vestigor.offermate.modules.resume.model.dto;

import java.util.List;

public record ResumeAnalysisResponseDTO(
        int overallScore,
        ScoreDetailDTO scoreDetail,
        String summary,
        List<String> strengths,
        List<SuggestionDTO> suggestions
) {}
