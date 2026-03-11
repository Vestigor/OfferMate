package io.github.vestigor.offermate.modules.knowledgebase.service.impl;

import io.github.vestigor.offermate.common.exception.BusinessException;
import io.github.vestigor.offermate.common.exception.ErrorCode;
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

/**
 * 知识库查询服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseListServiceImpl implements KnowledgeBaseListService {
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final RagChatMessageRepository ragChatMessageRepository;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final FileStorageService fileStorageService;

    /**
     * 获取知识库列表
     */
    @Override
    public List<KnowledgeBaseListItemDTO> listKnowledgeBases(VectorStatus vectorStatus, String sortBy) {
        List<KnowledgeBaseEntity> entities;

        // 如果指定了状态，按状态过滤
        if (vectorStatus != null) {
            entities = knowledgeBaseRepository.findByVectorStatusOrderByUploadedAtDesc(vectorStatus);
        } else {
            // 否则获取所有知识库
            entities = knowledgeBaseRepository.findAllByOrderByUploadedAtDesc();
        }

        // 如果指定了排序字段，在内存中排序
        if (sortBy != null && !sortBy.isBlank() && !sortBy.equalsIgnoreCase("time")) {
            entities = sortEntities(entities, sortBy);
        }

        return knowledgeBaseMapper.toListItemDTOList(entities);
    }

    /**
     * 获取所有知识库列表
     */
    @Override
    public List<KnowledgeBaseListItemDTO> listKnowledgeBases() {
        return listKnowledgeBases(null, null);
    }

    /**
     * 按向量化状态获取知识库列表
     */
    @Override
    public List<KnowledgeBaseListItemDTO> listKnowledgeBasesByStatus(VectorStatus vectorStatus) {
        return listKnowledgeBases(vectorStatus, null);
    }

    /**
     * 根据ID获取知识库详情
     */
    @Override
    public Optional<KnowledgeBaseListItemDTO> getKnowledgeBase(Long id) {
        return knowledgeBaseRepository.findById(id)
                .map(knowledgeBaseMapper::toListItemDTO);
    }

    /**
     * 根据ID获取知识库实体
     */
    @Override
    public Optional<KnowledgeBaseEntity> getKnowledgeBaseEntity(Long id) {
        return knowledgeBaseRepository.findById(id);
    }

    /**
     * 根据ID列表获取知识库名称列表
     */
    @Override
    public List<String> getKnowledgeBaseNames(List<Long> ids) {
        return ids.stream()
                .map(id -> knowledgeBaseRepository.findById(id)
                        .map(KnowledgeBaseEntity::getName)
                        .orElse("未知知识库"))
                .toList();
    }


    /**
     * 获取所有分类
     */
    @Override
    public List<String> getAllCategories() {
        return knowledgeBaseRepository.findAllCategories();
    }

    /**
     * 根据分类获取知识库列表
     */
    @Override
    public List<KnowledgeBaseListItemDTO> listByCategory(String category) {
        List<KnowledgeBaseEntity> entities;
        if (category == null || category.isBlank()) {
            entities = knowledgeBaseRepository.findByCategoryIsNullOrderByUploadedAtDesc();
        } else {
            entities = knowledgeBaseRepository.findByCategoryOrderByUploadedAtDesc(category);
        }
        return knowledgeBaseMapper.toListItemDTOList(entities);
    }

    /**
     * 更新知识库分类
     */
    @Override
    @Transactional
    public void updateCategory(Long id, String category) {
        KnowledgeBaseEntity entity = knowledgeBaseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("知识库不存在"));
        entity.setCategory(category != null && !category.isBlank() ? category : null);
        knowledgeBaseRepository.save(entity);
        log.info("更新知识库分类: id={}, category={}", id, category);
    }


    /**
     * 按关键词搜索知识库
     */
    @Override
    public List<KnowledgeBaseListItemDTO> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return listKnowledgeBases();
        }
        return knowledgeBaseMapper.toListItemDTOList(
                knowledgeBaseRepository.searchByKeyword(keyword.trim())
        );
    }


    /**
     * 按指定字段排序获取知识库列表
     */
    @Override
    public List<KnowledgeBaseListItemDTO> listSorted(String sortBy) {
        return listKnowledgeBases(null, sortBy);
    }

    /**
     * 在内存中对实体列表排序
     */
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
            default -> entities; // time 已经在数据库层面排序了
        };
    }


    /**
     * 获取知识库统计信息，总提问次数从用户消息数统计，确保多知识库提问只算一次
     */
    @Override
    public KnowledgeBaseStatsDTO getStatistics() {
        return new KnowledgeBaseStatsDTO(
                knowledgeBaseRepository.count(),
                ragChatMessageRepository.countByType(RagChatMessageEntity.MessageType.USER),  // 真正的提问次数
                knowledgeBaseRepository.sumAccessCount(),
                knowledgeBaseRepository.countByVectorStatus(VectorStatus.COMPLETED),
                knowledgeBaseRepository.countByVectorStatus(VectorStatus.PROCESSING)
        );
    }


    /**
     * 下载知识库文件
     */
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

    /**
     * 获取知识库文件信息
     */
    @Override
    public KnowledgeBaseEntity getEntityForDownload(Long id) {
        return knowledgeBaseRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND, "知识库不存在"));
    }
}