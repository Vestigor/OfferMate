package io.github.vestigor.offermate.modules.knowledgebase.service.impl;

import io.github.vestigor.offermate.infrastructure.file.ContentTypeDetectionService;
import io.github.vestigor.offermate.infrastructure.file.DocumentParseService;
import io.github.vestigor.offermate.infrastructure.file.FileStorageService;
import io.github.vestigor.offermate.modules.knowledgebase.service.KnowledgeBaseParseService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库解析服务
 * 委托给通用的 DocumentParseService 处理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseParseServiceImpl implements KnowledgeBaseParseService {

    private final DocumentParseService documentParseService;
    private final ContentTypeDetectionService contentTypeDetectionService;
    private final FileStorageService storageService;

    /**
     * 解析上传的知识库文件，提取文本内容
     */
    @Override
    public String parseContent(MultipartFile file) {
        log.info("开始解析知识库文件: {}", file.getOriginalFilename());
        return documentParseService.parseContent(file);
    }

    /**
     * 解析字节数组形式的文件内容
     */
    @Override
    public String parseContent(byte[] fileBytes, String fileName) {
        log.info("开始解析知识库文件（从字节数组）: {}", fileName);
        return documentParseService.parseContent(fileBytes, fileName);
    }

    /**
     * 从存储下载文件并解析内容
     */
    @Override
    public String downloadAndParseContent(String storageKey, String originalFilename) {
        log.info("从存储下载并解析知识库文件: {}", originalFilename);
        return documentParseService.downloadAndParseContent(storageService, storageKey, originalFilename);
    }

    /**
     * 检测文件的MIME类型
     */
    @Override
    public String detectContentType(MultipartFile file) {
        return contentTypeDetectionService.detectContentType(file);
    }
}
