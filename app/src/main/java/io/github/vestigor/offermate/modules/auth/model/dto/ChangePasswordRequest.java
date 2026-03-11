package io.github.vestigor.offermate.modules.auth.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 修改密码请求
 */
public record ChangePasswordRequest(
        @NotBlank(message = "旧密码不能为空")
        String oldPassword,

        @NotBlank(message = "新密码不能为空")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).{8,20}$",
                message = "新密码必须8-20位，包含大小写字母、数字和特殊字符"
        )
        String newPassword
){}