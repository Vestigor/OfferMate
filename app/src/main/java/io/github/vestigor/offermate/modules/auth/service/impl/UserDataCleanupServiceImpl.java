package io.github.vestigor.offermate.modules.auth.service.impl;

import io.github.vestigor.offermate.modules.auth.service.UserDataCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 用户数据清理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDataCleanupServiceImpl implements UserDataCleanupService {

    @Override
    public void cleanupUserData() {
        return;
    }
}
