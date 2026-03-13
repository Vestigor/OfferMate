package io.github.vestigor.offermate.modules.knowledgebase.service.impl;

import io.github.vestigor.offermate.common.exception.BusinessException;
import io.github.vestigor.offermate.common.exception.ErrorCode;
import io.github.vestigor.offermate.common.security.SecurityUtils;
import io.github.vestigor.offermate.infrastructure.file.FileStorageService;
import io.github.vestigor.offermate.infrastructure.mapper.KnowledgeBaseMapper;
import io.github.vestigor.offermate.modules.knowledgebase.model.dto.KnowledgeBaseListItemDTO;
import io.github.vestigor.offermate.modules.knowledgebase.model.dto.KnowledgeBaseStatsDTO;
import io.github.vestigor.offermate.modules.knowledgebase.model.entity.KnowledgeBaseEntity;
import io.github.vestigor.offermate.modules.knowledgebase.model.entity.RagChatMessageEntity;
import io.github.vestigor.offermate.modules.knowledgebase.model.status.VectorStatus;
import io.github.vestigor.offermate.modules.knowledgebase.repository.KnowledgeBaseRepository;
import io.github.vestigor.offermate.modules.knowledgebase.repository.RagChatMessageRepository;
import io.github.vestigor.offermate.modules.knowledgebase.service.KnowledgeBaseListService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseListServiceImpl implements KnowledgeBaseListService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final RagChatMessageRepository ragChatMessageRepository;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final FileStorageService fileStorageService;

    @Override
    public List<KnowledgeBaseListItemDTO> listKnowledgeBases(VectorStatus vectorStatus, String sortBy) {
        Long userId = SecurityUtils.getUserId();
        List<KnowledgeBaseEntity> entities;

        if (vectorStatus != null) {
            entities = knowledgeBaseRepository.findByUserIdAndVectorStatusOrderByUploadedAtDesc(userId, vectorStatus);
        } else {
            entities = knowledgeBaseRepository.findByUserIdOrderByUploadedAtDesc(userId);
        }

        if (sortBy != null && !sortBy.isBlank() && !sortBy.equalsIgnoreCase("time")) {
            entities = sortEntities(entities, sortBy);
        }

        return knowledgeBaseMapper.toListItemDTOList(entities);
    }

    @Override
    public Optional<KnowledgeBaseListItemDTO> getKnowledgeBase(Long id) {
        Long userId = SecurityUtils.getUserId();
        return knowledgeBaseRepository.findById(id)
                .filter(kb -> kb.getUserId().equals(userId))
                .map(knowledgeBaseMapper::toListItemDTO);
    }

    @Override
    public List<String> getKnowledgeBaseNames(List<Long> ids) {
        return ids.stream()
                .map(id -> knowledgeBaseRepository.findById(id)
                        .map(KnowledgeBaseEntity::getName)
                        .orElse("未知知识库"))
                .toList();
    }

    @Override
    public List<String> getAllCategories() {
        return knowledgeBaseRepository.findCategoriesByUserId(SecurityUtils.getUserId());
    }

    @Override
    public List<KnowledgeBaseListItemDTO> listByCategory(String category) {
        Long userId = SecurityUtils.getUserId();
        List<KnowledgeBaseEntity> entities;
        if (category == null || category.isBlank()) {
            entities = knowledgeBaseRepository.findByUserIdAndCategoryIsNullOrderByUploadedAtDesc(userId);
        } else {
            entities = knowledgeBaseRepository.findByUserIdAndCategoryOrderByUploadedAtDesc(userId, category);
        }
        return knowledgeBaseMapper.toListItemDTOList(entities);
    }

    @Override
    @Transactional
    public void updateCategory(Long id, String category) {
        Long userId = SecurityUtils.getUserId();
        KnowledgeBaseEntity entity = knowledgeBaseRepository.findById(id)
                .filter(kb -> kb.getUserId().equals(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND, "知识库不存在"));
        entity.setCategory(category != null && !category.isBlank() ? category : null);
        knowledgeBaseRepository.save(entity);
        log.info("更新知识库分类: id={}, category={}", id, category);
    }

    @Override
    public List<KnowledgeBaseListItemDTO> search(String keyword) {
        Long userId = SecurityUtils.getUserId();
        if (keyword == null || keyword.isBlank()) {
            return knowledgeBaseMapper.toListItemDTOList(
                    knowledgeBaseRepository.findByUserIdOrderByUploadedAtDesc(userId));
        }
        return knowledgeBaseMapper.toListItemDTOList(
                knowledgeBaseRepository.searchByUserIdAndKeyword(userId, keyword.trim()));
    }

    @Override
    public KnowledgeBaseStatsDTO getStatistics() {
        Long userId = SecurityUtils.getUserId();
        return new KnowledgeBaseStatsDTO(
                knowledgeBaseRepository.countByUserId(userId),
                ragChatMessageRepository.countBySession_UserIdAndType(userId, RagChatMessageEntity.MessageType.USER),
                knowledgeBaseRepository.sumAccessCountByUserId(userId),
                knowledgeBaseRepository.countByUserIdAndVectorStatus(userId, VectorStatus.COMPLETED),
                knowledgeBaseRepository.countByUserIdAndVectorStatus(userId, VectorStatus.PROCESSING)
        );
    }

    @Override
    public byte[] downloadFile(Long id) {
        KnowledgeBaseEntity entity = knowledgeBaseRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND, "知识库不存在"));

        String storageKey = entity.getStorageKey();
        if (storageKey == null || storageKey.isBlank()) {
            throw new BusinessException(ErrorCode.STORAGE_DOWNLOAD_FAILED, "文件存储信息不存在");
        }

        log.info("下载知识库文件: id={}, filename={}", id, entity.getOriginalFilename());
        return fileStorageService.downloadFile(storageKey);
    }

    @Override
    public KnowledgeBaseEntity getEntityForDownload(Long id) {
        return knowledgeBaseRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND, "知识库不存在"));
    }

    private List<KnowledgeBaseEntity> sortEntities(List<KnowledgeBaseEntity> entities, String sortBy) {
        return switch (sortBy.toLowerCase()) {
            case "size" -> entities.stream()
                    .sorted((a, b) -> Long.compare(b.getFileSize(), a.getFileSize()))
                    .toList();
            case "access" -> entities.stream()
                    .sorted((a, b) -> Integer.compare(b.getAccessCount(), a.getAccessCount()))
                    .toList();
            case "question" -> entities.stream()
                    .sorted((a, b) -> Integer.compare(b.getQuestionCount(), a.getQuestionCount()))
                    .toList();
            default -> entities;
        };
    }
}
