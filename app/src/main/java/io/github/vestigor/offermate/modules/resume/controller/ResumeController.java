package io.github.vestigor.offermate.modules.resume.controller;

import io.github.vestigor.offermate.common.annotiation.RateLimit;
import io.github.vestigor.offermate.common.result.Result;
import io.github.vestigor.offermate.modules.resume.model.dto.ResumeDetailDTO;
import io.github.vestigor.offermate.modules.resume.model.dto.ResumeListItemDTO;
import io.github.vestigor.offermate.modules.resume.service.ResumeDeleteService;
import io.github.vestigor.offermate.modules.resume.service.ResumeHistoryService;
import io.github.vestigor.offermate.modules.resume.service.ResumeUploadService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 简历控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/resumes")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeUploadService uploadService;
    private final ResumeHistoryService historyService;
    private final ResumeDeleteService deleteService;

    /**
     * 上传简历并获取分析结果
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RateLimit(dimensions = {RateLimit.Dimension.GLOBAL, RateLimit.Dimension.IP}, count = 5)
    public Result<Map<String, Object>> uploadAndAnalyze(@RequestParam("file") MultipartFile file) {
        Map<String, Object> result = uploadService.uploadAndAnalyze(file);
        boolean isDuplicate = (Boolean) result.get("duplicate");
        if (isDuplicate) {
            return Result.success("检测到相同简历，已返回历史分析结果", result);
        }
        return Result.success(result);
    }

    /**
     * 获取所有简历列表
     */
    @GetMapping()
    public Result<List<ResumeListItemDTO>> getUserAllResumes() {
        List<ResumeListItemDTO> resumes = historyService.getUserAllResumes();
        return Result.success(resumes);
    }

    /**
     * 获取简历详情（包含分析历史）
     */
    @GetMapping("/{id}/detail")
    public Result<ResumeDetailDTO> getResumeDetail(@PathVariable Long id) {
        ResumeDetailDTO detail = historyService.getResumeDetail(id);
        return Result.success(detail);
    }

    /**
     * 导出简历分析报告为PDF
     */
    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> exportAnalysisPdf(@PathVariable Long id) {
        try {
            var result = historyService.exportAnalysisPdf(id);
            String filename = URLEncoder.encode(result.filename(), StandardCharsets.UTF_8);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(result.pdfBytes());
        } catch (Exception e) {
            log.error("导出PDF失败: resumeId={}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 删除简历
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteResume(@PathVariable Long id) {
        deleteService.deleteResume(id);
        return Result.success(null);
    }

    /**
     * 重新分析简历（手动重试）
     * 用于分析失败后的重试
     */
    @PostMapping("/{id}/reanalyze")
    @RateLimit(dimensions = {RateLimit.Dimension.GLOBAL, RateLimit.Dimension.IP}, count = 2)
    public Result<Void> reanalyze(@PathVariable Long id) {
        uploadService.reanalyze(id);
        return Result.success(null);
    }

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public Result<Map<String, String>> health() {
        return Result.success(Map.of(
                "status", "UP",
                "service", "OfferMate - Resume Service"
        ));
    }

}
