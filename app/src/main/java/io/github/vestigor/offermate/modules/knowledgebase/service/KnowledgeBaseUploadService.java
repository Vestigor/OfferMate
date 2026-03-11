package io.github.vestigor.offermate.modules.knowledgebase.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface KnowledgeBaseUploadService {

    /**
     * 上传知识库文件
     */
    Map<String, Object> uploadKnowledgeBase(MultipartFile file, String name, String category);

    /**
     * 重新向量化知识库
     * 从 RustFS 重新下载文件并发送向量化任务
     */
    void reVectorize(Long kbId);
}
