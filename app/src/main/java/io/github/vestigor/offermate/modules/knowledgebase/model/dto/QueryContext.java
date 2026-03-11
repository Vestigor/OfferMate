package io.github.vestigor.offermate.modules.knowledgebase.model.dto;

import java.util.List;

public record QueryContext(String originalQuestion, List<String> candidateQueries, SearchParams searchParams) {
}
