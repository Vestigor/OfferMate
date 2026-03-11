package io.github.vestigor.offermate.modules.auth.controller;

import io.github.vestigor.offermate.common.result.Result;
import io.github.vestigor.offermate.modules.auth.model.entity.UserEntity;
import io.github.vestigor.offermate.modules.auth.model.dto.AuthResponse;
import io.github.vestigor.offermate.modules.auth.model.dto.ChangePasswordRequest;
import io.github.vestigor.offermate.modules.auth.model.dto.LoginRequest;
import io.github.vestigor.offermate.modules.auth.model.dto.RegisterRequest;
import io.github.vestigor.offermate.modules.auth.service.AuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Result<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return Result.success("注册成功", response);
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return Result.success("登录成功", response);
    }

    /**
     * 登出
     */
    @PostMapping("/logout")
    public Result<Object> logout() {
        authService.logout();
        return Result.success("登出成功", null);
    }

    /**
     * 修改密码
     */
    @PutMapping("/password")
    public Result<Object> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return Result.success("密码修改成功", null);
    }

    /**
     * 注销账户
     */
    @DeleteMapping("/account")
    public Result<Object> deleteAccount() {
        authService.deleteAccount();
        return Result.success("账户已注销", null);
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/me")
    public Result<UserEntity> getCurrentUser() {
        UserEntity user = authService.getCurrentUser();
        return Result.success(user);
    }

    /**
     * 刷新 Token
     * 客户端在 access token 过期后，携带 refresh token 换取新的令牌对
     */
    @PostMapping("/refresh-token")
    public Result<AuthResponse> refreshToken(@RequestParam String refreshToken) {
        AuthResponse response = authService.refreshToken(refreshToken);
        return Result.success("Token 刷新成功", response);
    }
}
