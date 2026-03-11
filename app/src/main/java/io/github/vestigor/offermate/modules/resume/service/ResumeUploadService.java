package io.github.vestigor.offermate.modules.resume.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface ResumeUploadService {

    /**
     * 上传并异步分析简历
     */
    Map<String, Object> uploadAndAnalyze(MultipartFile file);

    /**
     * 重新分析简历（手动重试）
     * 从数据库获取简历文本并发送分析任务
     */
    void reanalyze(Long resumeId);
}
