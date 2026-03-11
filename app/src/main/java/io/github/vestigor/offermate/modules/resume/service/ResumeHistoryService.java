package io.github.vestigor.offermate.modules.resume.service;

import io.github.vestigor.offermate.modules.resume.model.dto.ExportResult;
import io.github.vestigor.offermate.modules.resume.model.dto.ResumeDetailDTO;
import io.github.vestigor.offermate.modules.resume.model.dto.ResumeListItemDTO;

import java.util.List;

public interface ResumeHistoryService {

    /**
     * 获取所有简历列表
     */
    List<ResumeListItemDTO> getUserAllResumes();

    /**
     * 获取简历详情
     */
    ResumeDetailDTO getResumeDetail(Long id);

    /**
     * 导出简历分析报告为PDF
     */
    ExportResult exportAnalysisPdf(Long resumeId);
}
