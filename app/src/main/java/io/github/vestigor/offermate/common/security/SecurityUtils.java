package io.github.vestigor.offermate.common.security;

import io.github.vestigor.offermate.common.exception.BusinessException;
import io.github.vestigor.offermate.common.exception.ErrorCode;
import io.github.vestigor.offermate.modules.auth.model.entity.UserEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtils {
    public static Long getUserId() {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserEntity) {
            return ((UserEntity) auth.getPrincipal()).getId();
        }
        throw new BusinessException(ErrorCode.UNKNOW_USER_VISIT);
    }

    public static String getUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            return auth.getName();
        }
        throw new BusinessException(ErrorCode.UNKNOW_USER_VISIT);
    }
}
