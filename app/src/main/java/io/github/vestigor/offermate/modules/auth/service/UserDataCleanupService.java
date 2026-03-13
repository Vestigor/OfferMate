package io.github.vestigor.offermate.modules.auth.service;

public interface UserDataCleanupService {

    /**
     * 清理用户的所有业务数据
     * 包括：简历（及其关联面试会话）、知识库（及其向量数据）
     *
     * @param userId 用户ID
     */
    void cleanupUserData(Long userId);
}
