package io.github.vestigor.offermate.modules.resume.service;

import org.springframework.web.multipart.MultipartFile;

public interface ResumeParseService {

    /**
     * 解析上传的简历文件，提取文本内容
     */
    String parseResume(MultipartFile file);

    /**
     * 解析字节数组形式的简历文件
     */
    String parseResume(byte[] fileBytes, String fileName);

    /**
     * 从存储下载文件并解析内容
     */
    String downloadAndParseContent(String storageKey, String originalFilename);

    /**
     * 检测文件的MIME类型
     */
    String detectContentType(MultipartFile file);
}
