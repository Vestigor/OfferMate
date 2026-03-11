package io.github.vestigor.offermate.modules.knowledgebase.service;

import org.springframework.web.multipart.MultipartFile;

public interface KnowledgeBaseParseService {

    /**
     * 解析上传的知识库文件，提取文本内容
     */
    String parseContent(MultipartFile file);

    /**
     * 解析字节数组形式的文件内容
     */
    String parseContent(byte[] fileBytes, String fileName);

    /**
     * 从存储下载文件并解析内容
     */
    String downloadAndParseContent(String storageKey, String originalFilename);

    /**
     * 检测文件的MIME类型
     */
    String detectContentType(MultipartFile file);
}
