package io.github.vestigor.offermate.modules.knowledgebase.service;

import io.github.vestigor.offermate.modules.knowledgebase.model.dto.KnowledgeBaseListItemDTO;
import io.github.vestigor.offermate.modules.knowledgebase.model.dto.KnowledgeBaseStatsDTO;
import io.github.vestigor.offermate.modules.knowledgebase.model.entity.KnowledgeBaseEntity;
import io.github.vestigor.offermate.modules.knowledgebase.model.status.VectorStatus;

import java.util.List;
import java.util.Optional;

public interface KnowledgeBaseListService {

    List<KnowledgeBaseListItemDTO> listKnowledgeBases(VectorStatus vectorStatus, String sortBy);

    Optional<KnowledgeBaseListItemDTO> getKnowledgeBase(Long id);

    List<String> getKnowledgeBaseNames(List<Long> ids);

    List<String> getAllCategories();

    List<KnowledgeBaseListItemDTO> listByCategory(String category);

    void updateCategory(Long id, String category);

    List<KnowledgeBaseListItemDTO> search(String keyword);

    KnowledgeBaseStatsDTO getStatistics();

    byte[] downloadFile(Long id);

    KnowledgeBaseEntity getEntityForDownload(Long id);
}
