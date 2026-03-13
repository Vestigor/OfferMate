package io.github.vestigor.offermate.modules.resume.service.impl;

import io.github.vestigor.offermate.common.exception.BusinessException;
import io.github.vestigor.offermate.common.exception.ErrorCode;
import io.github.vestigor.offermate.infrastructure.export.PdfExportService;
import io.github.vestigor.offermate.infrastructure.mapper.InterviewMapper;
import io.github.vestigor.offermate.infrastructure.mapper.ResumeMapper;
import io.github.vestigor.offermate.modules.interview.service.InterviewPersistenceService;
import io.github.vestigor.offermate.modules.resume.model.dto.ExportResult;
import io.github.vestigor.offermate.modules.resume.model.dto.ResumeAnalysisResponse;
import io.github.vestigor.offermate.modules.resume.model.dto.ResumeDetailDTO;
import io.github.vestigor.offermate.modules.resume.model.dto.ResumeListItemDTO;
import io.github.vestigor.offermate.modules.resume.model.entity.ResumeAnalysisEntity;
import io.github.vestigor.offermate.modules.resume.model.entity.ResumeEntity;
import io.github.vestigor.offermate.modules.resume.service.ResumeHistoryService;
import io.github.vestigor.offermate.modules.resume.service.ResumePersistenceService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeHistoryServiceImpl implements ResumeHistoryService {

    private final ResumePersistenceService resumePersistenceService;
    private final InterviewPersistenceService interviewPersistenceService;
    private final PdfExportService pdfExportService;
    private final ObjectMapper objectMapper;
    private final ResumeMapper resumeMapper;
    private final InterviewMapper interviewMapper;

    /**
     * 获取用户所有简历列表
     */
    @Override
    public List<ResumeListItemDTO> getUserAllResumes() {
        List<ResumeEntity> resumes = resumePersistenceService.findUserAllResumes();

        return resumes.stream().map(resume -> {
            Integer latestScore = null;
            LocalDateTime lastAnalyzedAt = null;
            Optional<ResumeAnalysisEntity> analysisOpt = resumePersistenceService.getLatestAnalysis(resume.getId());
            if (analysisOpt.isPresent()) {
                ResumeAnalysisEntity analysis = analysisOpt.get();
                latestScore = analysis.getOverallScore();
                lastAnalyzedAt = analysis.getAnalyzedAt();
            }
            int interviewCount = interviewPersistenceService.findByResumeId(resume.getId()).size();
            return resumeMapper.toListItemDTO(resume, latestScore, lastAnalyzedAt, interviewCount);
        }).toList();
    }

    /**
     * 获取简历详情（包含分析历史）
     */
    @Override
    public ResumeDetailDTO getResumeDetail(Long id) {
        ResumeEntity resume = resumePersistenceService.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESUME_NOT_FOUND));

        List<ResumeAnalysisEntity> analyses = resumePersistenceService.findAnalysesByResumeId(id);
        List<ResumeDetailDTO.AnalysisHistoryDTO> analysisHistory = resumeMapper.toAnalysisHistoryDTOList(
                analyses,
                this::extractStrengths,
                this::extractSuggestions
        );

        List<Object> interviewHistory = interviewMapper.toInterviewHistoryList(
                interviewPersistenceService.findByResumeId(id)
        );

        return resumeMapper.toDetailDTO(resume, analysisHistory, interviewHistory);
    }

    /**
     * 从 JSON 提取 strengths
     */
    private List<String> extractStrengths(ResumeAnalysisEntity entity) {
        try {
            if (entity.getStrengthsJson() != null) {
                return objectMapper.readValue(entity.getStrengthsJson(), new TypeReference<>() {});
            }
        } catch (JacksonException e) {
            log.error("解析 strengths JSON 失败", e);
        }
        return List.of();
    }

    /**
     * 从 JSON 提取 suggestions
     */
    private List<Object> extractSuggestions(ResumeAnalysisEntity entity) {
        try {
            if (entity.getSuggestionsJson() != null) {
                return objectMapper.readValue(entity.getSuggestionsJson(), new TypeReference<>() {});
            }
        } catch (JacksonException e) {
            log.error("解析 suggestions JSON 失败", e);
        }
        return List.of();
    }

    /**
     * 导出简历分析报告为PDF
     */
    @Override
    public ExportResult exportAnalysisPdf(Long resumeId) {
        ResumeEntity resume = resumePersistenceService.findById(resumeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESUME_NOT_FOUND));

        ResumeAnalysisResponse analysis = resumePersistenceService.getLatestAnalysisAsDTO(resumeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESUME_ANALYSIS_NOT_FOUND));

        try {
            byte[] pdfBytes = pdfExportService.exportResumeAnalysis(resume, analysis);
            String filename = "简历分析报告_" + resume.getOriginalFilename() + ".pdf";
            return new ExportResult(pdfBytes, filename);
        } catch (Exception e) {
            log.error("导出PDF失败: resumeId={}", resumeId, e);
            throw new BusinessException(ErrorCode.EXPORT_PDF_FAILED, "导出PDF失败: " + e.getMessage());
        }
    }
}
