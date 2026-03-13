package io.github.vestigor.offermate.infrastructure.mapper;

import io.github.vestigor.offermate.modules.resume.model.dto.ResumeAnalysisResponse;
import io.github.vestigor.offermate.modules.resume.model.dto.ResumeDetailDTO;
import io.github.vestigor.offermate.modules.resume.model.dto.ResumeListItemDTO;
import io.github.vestigor.offermate.modules.resume.model.entity.ResumeAnalysisEntity;
import io.github.vestigor.offermate.modules.resume.model.entity.ResumeEntity;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;

/**
 * 简历相关的对象映射器
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ResumeMapper {

    // ========== ScoreDetail 映射 ==========

    @Mapping(target = "contentScore",    source = "contentScore",    qualifiedByName = "nullToZero")
    @Mapping(target = "structureScore",  source = "structureScore",  qualifiedByName = "nullToZero")
    @Mapping(target = "skillMatchScore", source = "skillMatchScore", qualifiedByName = "nullToZero")
    @Mapping(target = "expressionScore", source = "expressionScore", qualifiedByName = "nullToZero")
    @Mapping(target = "projectScore",    source = "projectScore",    qualifiedByName = "nullToZero")
    ResumeAnalysisResponse.ScoreDetail toScoreDetail(ResumeAnalysisEntity entity);

    // ========== ResumeListItemDTO 映射 ==========

    /**
     * ResumeEntity 转换为 ResumeListItemDTO（含统计聚合字段）
     */
    default ResumeListItemDTO toListItemDTO(
            ResumeEntity resume,
            Integer latestScore,
            LocalDateTime lastAnalyzedAt,
            Integer interviewCount) {
        return new ResumeListItemDTO(
                resume.getId(),
                resume.getOriginalFilename(),
                resume.getFileSize(),
                resume.getUploadedAt(),
                resume.getAccessCount(),
                latestScore,
                lastAnalyzedAt,
                interviewCount
        );
    }

    // ========== ResumeDetailDTO 映射 ==========

    /**
     * ResumeEntity 转换为 ResumeDetailDTO（含分析历史和面试历史）
     * analyses 和 interviews 需要在 Service 层从 JSON 解析后传入
     */
    default ResumeDetailDTO toDetailDTO(
            ResumeEntity entity,
            List<ResumeDetailDTO.AnalysisHistoryDTO> analyses,
            List<Object> interviews) {
        return new ResumeDetailDTO(
                entity.getId(),
                entity.getOriginalFilename(),
                entity.getFileSize(),
                entity.getContentType(),
                entity.getStorageUrl(),
                entity.getUploadedAt(),
                entity.getAccessCount(),
                entity.getResumeText(),
                entity.getAnalyzeStatus(),
                entity.getAnalyzeError(),
                analyses,
                interviews
        );
    }

    // ========== AnalysisHistoryDTO 映射 ==========

    /**
     * ResumeAnalysisEntity 转换为 AnalysisHistoryDTO
     * strengths 和 suggestions 需要在 Service 层从 JSON 解析后传入
     */
    @Mapping(target = "strengths",   source = "strengths")
    @Mapping(target = "suggestions", source = "suggestions")
    ResumeDetailDTO.AnalysisHistoryDTO toAnalysisHistoryDTO(
            ResumeAnalysisEntity entity,
            List<String> strengths,
            List<Object> suggestions
    );

    /**
     * 批量转换（JSON 解析委托给 Service 层）
     */
    default List<ResumeDetailDTO.AnalysisHistoryDTO> toAnalysisHistoryDTOList(
            List<ResumeAnalysisEntity> entities,
            Function<ResumeAnalysisEntity, List<String>> strengthsExtractor,
            Function<ResumeAnalysisEntity, List<Object>> suggestionsExtractor) {
        return entities.stream()
                .map(e -> toAnalysisHistoryDTO(e, strengthsExtractor.apply(e), suggestionsExtractor.apply(e)))
                .toList();
    }

    // ========== ResumeAnalysisEntity 创建/更新映射 ==========

    /**
     * 从 ResumeAnalysisResponse 创建 ResumeAnalysisEntity
     * JSON 字段和 Resume 关联需要在 Service 层设置
     */
    @Mapping(target = "id",              ignore = true)
    @Mapping(target = "resume",          ignore = true)
    @Mapping(target = "strengthsJson",   ignore = true)
    @Mapping(target = "suggestionsJson", ignore = true)
    @Mapping(target = "analyzedAt",      ignore = true)
    @Mapping(target = "contentScore",    source = "scoreDetail.contentScore")
    @Mapping(target = "structureScore",  source = "scoreDetail.structureScore")
    @Mapping(target = "skillMatchScore", source = "scoreDetail.skillMatchScore")
    @Mapping(target = "expressionScore", source = "scoreDetail.expressionScore")
    @Mapping(target = "projectScore",    source = "scoreDetail.projectScore")
    ResumeAnalysisEntity toAnalysisEntity(ResumeAnalysisResponse response);

    /**
     * 从 ResumeAnalysisResponse 更新已有的 ResumeAnalysisEntity
     */
    @Mapping(target = "id",              ignore = true)
    @Mapping(target = "resume",          ignore = true)
    @Mapping(target = "strengthsJson",   ignore = true)
    @Mapping(target = "suggestionsJson", ignore = true)
    @Mapping(target = "analyzedAt",      ignore = true)
    @Mapping(target = "contentScore",    source = "scoreDetail.contentScore")
    @Mapping(target = "structureScore",  source = "scoreDetail.structureScore")
    @Mapping(target = "skillMatchScore", source = "scoreDetail.skillMatchScore")
    @Mapping(target = "expressionScore", source = "scoreDetail.expressionScore")
    @Mapping(target = "projectScore",    source = "scoreDetail.projectScore")
    void updateAnalysisEntity(ResumeAnalysisResponse response, @MappingTarget ResumeAnalysisEntity entity);

    // ========== 工具方法 ==========

    @Named("nullToZero")
    default int nullToZero(Integer value) {
        return value != null ? value : 0;
    }
}
