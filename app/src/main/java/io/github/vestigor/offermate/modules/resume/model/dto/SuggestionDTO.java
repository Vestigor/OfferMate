package io.github.vestigor.offermate.modules.resume.model.dto;

public record SuggestionDTO(
        String category,
        String priority,
        String issue,
        String recommendation
) {}
