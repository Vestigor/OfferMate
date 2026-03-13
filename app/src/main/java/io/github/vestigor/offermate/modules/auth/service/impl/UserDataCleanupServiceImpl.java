package io.github.vestigor.offermate.modules.auth.service.impl;

import io.github.vestigor.offermate.modules.auth.service.UserDataCleanupService;
import io.github.vestigor.offermate.modules.knowledgebase.model.entity.KnowledgeBaseEntity;
import io.github.vestigor.offermate.modules.knowledgebase.model.entity.RagChatSessionEntity;
import io.github.vestigor.offermate.modules.knowledgebase.repository.KnowledgeBaseRepository;
import io.github.vestigor.offermate.modules.knowledgebase.repository.RagChatSessionRepository;
import io.github.vestigor.offermate.modules.knowledgebase.service.KnowledgeBaseDeleteService;
import io.github.vestigor.offermate.modules.resume.model.entity.ResumeEntity;
import io.github.vestigor.offermate.modules.resume.repository.ResumeRepository;
import io.github.vestigor.offermate.modules.resume.service.ResumeDeleteService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户数据清理服务
 * 注销账户时，复用各模块已有的删除接口，确保低耦合、完整级联清理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDataCleanupServiceImpl implements UserDataCleanupService {

    private final ResumeRepository resumeRepository;
    private final ResumeDeleteService resumeDeleteService;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeBaseDeleteService knowledgeBaseDeleteService;
    private final RagChatSessionRepository ragChatSessionRepository;

    /**
     * 清理用户的所有业务数据
     * 删除顺序：
     * 1. 简历（级联删除：面试会话、面试答案、存储文件）
     * 2. 知识库（级联删除：向量数据、存储文件、RAG会话关联）
     * 3. RAG聊天会话（级联删除：会话消息）
     */
    @Override
    public void cleanupUserData(Long userId) {
        log.info("开始清理用户数据: userId={}", userId);

        deleteUserResumes(userId);
        deleteUserKnowledgeBases(userId);
        deleteUserRagChatSessions(userId);

        log.info("用户数据清理完成: userId={}", userId);
    }

    private void deleteUserResumes(Long userId) {
        List<ResumeEntity> resumes = resumeRepository.findByUserId(userId);
        if (resumes.isEmpty()) {
            return;
        }
        log.info("开始删除用户简历: userId={}, count={}", userId, resumes.size());
        for (ResumeEntity resume : resumes) {
            try {
                resumeDeleteService.deleteResume(resume.getId());
            } catch (Exception e) {
                log.warn("删除简历失败，跳过继续: resumeId={}, error={}", resume.getId(), e.getMessage());
            }
        }
        log.info("用户简历删除完成: userId={}, count={}", userId, resumes.size());
    }

    private void deleteUserKnowledgeBases(Long userId) {
        List<KnowledgeBaseEntity> knowledgeBases = knowledgeBaseRepository.findByUserId(userId);
        if (knowledgeBases.isEmpty()) {
            return;
        }
        log.info("开始删除用户知识库: userId={}, count={}", userId, knowledgeBases.size());
        for (KnowledgeBaseEntity kb : knowledgeBases) {
            try {
                knowledgeBaseDeleteService.deleteKnowledgeBase(kb.getId());
            } catch (Exception e) {
                log.warn("删除知识库失败，跳过继续: kbId={}, error={}", kb.getId(), e.getMessage());
            }
        }
        log.info("用户知识库删除完成: userId={}, count={}", userId, knowledgeBases.size());
    }

    private void deleteUserRagChatSessions(Long userId) {
        List<RagChatSessionEntity> sessions = ragChatSessionRepository.findByUserId(userId);
        if (sessions.isEmpty()) {
            return;
        }
        log.info("开始删除用户RAG会话: userId={}, count={}", userId, sessions.size());
        ragChatSessionRepository.deleteAll(sessions);
        log.info("用户RAG会话删除完成: userId={}, count={}", userId, sessions.size());
    }
}
