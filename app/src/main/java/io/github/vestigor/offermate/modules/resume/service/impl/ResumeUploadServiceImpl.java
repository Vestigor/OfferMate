package io.github.vestigor.offermate.modules.resume.service.impl;

import io.github.vestigor.offermate.common.exception.BusinessException;
import io.github.vestigor.offermate.common.exception.ErrorCode;
import io.github.vestigor.offermate.common.model.AsyncTaskStatus;
import io.github.vestigor.offermate.common.properties.AppConfigProperties;
import io.github.vestigor.offermate.infrastructure.file.FileStorageService;
import io.github.vestigor.offermate.infrastructure.file.FileValidationService;
import io.github.vestigor.offermate.modules.resume.listener.AnalyzeStreamProducer;
import io.github.vestigor.offermate.modules.resume.model.dto.ResumeAnalysisResponse;
import io.github.vestigor.offermate.modules.resume.model.entity.ResumeEntity;
import io.github.vestigor.offermate.modules.resume.repository.ResumeRepository;
import io.github.vestigor.offermate.modules.resume.service.ResumeParseService;
import io.github.vestigor.offermate.modules.resume.service.ResumePersistenceService;
import io.github.vestigor.offermate.modules.resume.service.ResumeUploadService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * 简历上传服务
 * 处理简历上传、解析的业务逻辑
 * 通过 Redis Stream 实现 AI 分析异步处理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeUploadServiceImpl implements ResumeUploadService {

    private final ResumeParseService parseService;
    private final ResumePersistenceService persistenceService;
    private final AnalyzeStreamProducer analyzeStreamProducer;
    private final ResumeRepository resumeRepository;
    private final AppConfigProperties appConfigProperties;
    private final FileStorageService storageService;
    private final FileValidationService fileValidationService;

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    /**
     * 上传并异步分析简历
     *
     * @param file 简历文件
     * @return 上传结果（分析将异步进行）
     */
    public Map<String, Object> uploadAndAnalyze(org.springframework.web.multipart.MultipartFile file) {
        // 验证文件
        fileValidationService.validateFile(file, MAX_FILE_SIZE, "简历");

        String fileName = file.getOriginalFilename();
        log.info("收到简历上传请求: {}, 大小: {} bytes", fileName, file.getSize());

        // 验证文件类型
        String contentType = parseService.detectContentType(file);
        validateContentType(contentType);

        // 如果简历已经存在，返回历史分析结果
        Optional<ResumeEntity> existingResume = persistenceService.findExistingResume(file);
        if (existingResume.isPresent()) {
            return handleDuplicateResume(existingResume.get());
        }

        // 解析简历文本
        String resumeText = parseService.parseResume(file);
        if (resumeText == null || resumeText.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.RESUME_PARSE_FAILED, "无法从文件中提取文本内容，请确保文件不是扫描版PDF");
        }

        // 保存简历到RustFS
        String fileKey = storageService.uploadResume(file);
        String fileUrl = storageService.getFileUrl(fileKey);
        log.info("简历已存储到RustFS: {}", fileKey);

        // 保存简历到数据库
        ResumeEntity savedResume = persistenceService.saveResume(file, resumeText, fileKey, fileUrl);

        // 发送分析任务到 Redis Stream（异步处理）
        analyzeStreamProducer.sendAnalyzeTask(savedResume.getId(), resumeText);

        log.info("简历上传完成，分析任务已入队: {}, resumeId={}", fileName, savedResume.getId());

        // 返回结果（状态为 PENDING，前端可轮询获取最新状态）
        return Map.of(
                "resume", Map.of(
                        "id", savedResume.getId(),
                        "filename", savedResume.getOriginalFilename(),
                        "analyzeStatus", AsyncTaskStatus.PENDING.name()
                ),
                "storage", Map.of(
                        "fileKey", fileKey,
                        "fileUrl", fileUrl,
                        "resumeId", savedResume.getId()
                ),
                "duplicate", false
        );
    }

    /**
     * 重新分析简历，可以处理分析失败的情况
     * 从数据库获取简历文本并发送分析任务
     */
    @Transactional
    public void reanalyze(Long resumeId) {
        ResumeEntity resume = resumeRepository.findById(resumeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESUME_NOT_FOUND, "简历不存在"));

        log.info("开始重新分析简历: resumeId={}, filename={}", resumeId, resume.getOriginalFilename());

        String resumeText = resume.getResumeText();
        if (resumeText == null || resumeText.trim().isEmpty()) {
            // 如果没有缓存的文本，尝试重新解析
            resumeText = parseService.downloadAndParseContent(resume.getStorageKey(), resume.getOriginalFilename());
            if (resumeText == null || resumeText.trim().isEmpty()) {
                throw new BusinessException(ErrorCode.RESUME_PARSE_FAILED, "无法获取简历文本内容");
            }
            // 更新缓存的文本
            resume.setResumeText(resumeText);
        }

        // 更新状态为 PENDING
        resume.setAnalyzeStatus(AsyncTaskStatus.PENDING);
        resume.setAnalyzeError(null);
        resumeRepository.save(resume);

        // 发送分析任务到 Stream
        analyzeStreamProducer.sendAnalyzeTask(resumeId, resumeText);

        log.info("重新分析任务已发送: resumeId={}", resumeId);
    }

    /**
     * 验证文件类型
     */
    private void validateContentType(String contentType) {
        fileValidationService.validateContentTypeByList(
                contentType,
                appConfigProperties.getAllowedTypes(),
                "不支持的文件类型: " + contentType
        );
    }

    /**
     * 处理重复简历
     */
    private Map<String, Object> handleDuplicateResume(ResumeEntity resume) {
        log.info("检测到重复简历，返回历史分析结果: resumeId={}", resume.getId());

        // 获取历史分析结果
        Optional<ResumeAnalysisResponse> analysisOpt = persistenceService.getLatestAnalysisAsDTO(resume.getId());

        // 已有分析结果，直接返回
        // 没有分析结果，返回当前状态
        return analysisOpt.map(resumeAnalysisResponse -> Map.of(
                "analysis", resumeAnalysisResponse,
                "storage", Map.of(
                        "fileKey", resume.getStorageKey() != null ? resume.getStorageKey() : "",
                        "fileUrl", resume.getStorageUrl() != null ? resume.getStorageUrl() : "",
                        "resumeId", resume.getId()
                ),
                "duplicate", true
        )).orElseGet(() -> Map.of(
                "resume", Map.of(
                        "id", resume.getId(),
                        "filename", resume.getOriginalFilename(),
                        "analyzeStatus", resume.getAnalyzeStatus() != null ? resume.getAnalyzeStatus().name() : AsyncTaskStatus.PENDING.name()
                ),
                "storage", Map.of(
                        "fileKey", resume.getStorageKey() != null ? resume.getStorageKey() : "",
                        "fileUrl", resume.getStorageUrl() != null ? resume.getStorageUrl() : "",
                        "resumeId", resume.getId()
                ),
                "duplicate", true
        ));
    }


}
