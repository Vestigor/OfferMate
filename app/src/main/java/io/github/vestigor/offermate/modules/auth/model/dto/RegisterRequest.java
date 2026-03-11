package io.github.vestigor.offermate.modules.auth.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 注册请求DTO
 */
public record RegisterRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 20, message = "用户名长度必须在3-20之间")
        @Pattern(
                regexp = "^[a-zA-Z][a-zA-Z0-9_]*$",
                message = "用户名必须以字母开头，并且只能包含字母、数字和下划线"
        )
        String username,

        @NotBlank(message = "密码不能为空")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).{8,20}$",
                message = "密码必须8-20位，包含大小写字母、数字和特殊字符"
        )
        String password
){}
