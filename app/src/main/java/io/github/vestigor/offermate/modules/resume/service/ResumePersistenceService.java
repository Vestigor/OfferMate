package io.github.vestigor.offermate.modules.resume.service;

import io.github.vestigor.offermate.modules.resume.model.dto.ResumeAnalysisResponse;
import io.github.vestigor.offermate.modules.resume.model.entity.ResumeAnalysisEntity;
import io.github.vestigor.offermate.modules.resume.model.entity.ResumeEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

public interface ResumePersistenceService {

    /**
     * 检查简历是否已存在（基于文件内容hash）
     */
    Optional<ResumeEntity> findExistingResume(MultipartFile file);

    /**
     * 保存新简历
     */
    ResumeEntity saveResume(MultipartFile file, String resumeText, String storageKey, String storageUrl);

    /**
     * 保存简历评测结果
     */
    ResumeAnalysisEntity saveAnalysis(ResumeEntity resume, ResumeAnalysisResponse analysis);

    /**
     * 获取简历的最新评测结果
     */
    Optional<ResumeAnalysisEntity> getLatestAnalysis(Long resumeId);

    /**
     * 获取简历的最新评测结果（返回DTO）
     */
    Optional<ResumeAnalysisResponse> getLatestAnalysisAsDTO(Long resumeId);

    /**
     * 获取用户所有简历列表
     */
    List<ResumeEntity> findUserAllResumes();

    /**
     * 获取简历的所有评测记录
     */
    List<ResumeAnalysisEntity> findAnalysesByResumeId(Long resumeId);

    /**
     * 将实体转换为DTO
     */
    ResumeAnalysisResponse entityToDTO(ResumeAnalysisEntity entity);

    /**
     * 根据ID获取简历
     */
    Optional<ResumeEntity> findById(Long id);

    /**
     * 删除简历及其所有关联数据
     */
    void deleteResume(Long id);
}
