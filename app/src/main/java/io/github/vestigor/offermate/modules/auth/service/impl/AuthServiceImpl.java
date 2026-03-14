package io.github.vestigor.offermate.modules.auth.service.impl;

import io.github.vestigor.offermate.common.exception.BusinessException;
import io.github.vestigor.offermate.common.exception.ErrorCode;
import io.github.vestigor.offermate.common.security.JwtService;
import io.github.vestigor.offermate.common.security.SecurityUtils;
import io.github.vestigor.offermate.modules.auth.model.dto.AuthResponse;
import io.github.vestigor.offermate.modules.auth.model.dto.ChangePasswordRequest;
import io.github.vestigor.offermate.modules.auth.model.dto.LoginRequest;
import io.github.vestigor.offermate.modules.auth.model.dto.RegisterRequest;
import io.github.vestigor.offermate.modules.auth.model.entity.UserEntity;
import io.github.vestigor.offermate.modules.auth.repository.UserRepository;
import io.github.vestigor.offermate.modules.auth.service.AuthService;
import io.github.vestigor.offermate.modules.auth.service.RefreshTokenService;
import io.github.vestigor.offermate.modules.auth.service.UserDataCleanupService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;
    private final UserDataCleanupService userDataCleanupService;

    /**
     * 用户注册
     */
    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info("用户注册请求: username={}", request.username());
        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessException(ErrorCode.DUPLICATE_USERNAME);
        }

        UserEntity user = new UserEntity();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));

        user = userRepository.save(user);
        log.info("用户注册成功: userId={}, username={}", user.getId(), user.getUsername());

        // 生成 access token + refresh token
        String accessToken = jwtService.generateToken(user.getId(), user.getUsername());
        String refreshToken = refreshTokenService.createRefreshToken(user.getId(), user.getUsername());

        return new AuthResponse(accessToken, refreshToken, user.getId(), user.getUsername());
    }

    /**
     * 用户登录
     */
    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        log.info("用户登录请求: username={}", request.username());

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.username(),
                            request.password()
                    )
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (InternalAuthenticationServiceException e) {
            if (e.getCause() instanceof BusinessException businessException &&
                    businessException.getCode() == ErrorCode.USER_NOT_FOUND.getCode()) {
                log.warn("登录失败，用户不存在：username={}", request.username());
                throw new BusinessException(ErrorCode.USER_NOT_FOUND);
            }
            log.error("登录失败，认证异常：username={}", request.username(), e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "登录认证失败，请稍后重试");
        } catch (UsernameNotFoundException e) {
            log.warn("登录失败，用户不存在: username={}", request.username());
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        } catch (BadCredentialsException e) {
            log.warn("登录失败，密码错误: username={}", request.username());
            throw new BusinessException(ErrorCode.BAD_CREDENTIALS);
        } catch (Exception e) {
            log.error("登录失败，认证异常: username={}", request.username(), e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "登录认证失败，请稍后重试");
        }

        UserEntity user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        user.updateLastLogin();
        userRepository.save(user);

        log.info("用户登录成功: username={}", user.getUsername());

        // 生成 access token + refresh token
        String accessToken = jwtService.generateToken(user.getId(), user.getUsername());
        String refreshToken = refreshTokenService.createRefreshToken(user.getId(), user.getUsername());

        return new AuthResponse(accessToken, refreshToken, user.getId(), user.getUsername());
    }

    /**
     * 登出 — 撤销 refresh token + 黑名单 access token
     */
    @Override
    public void logout() {
        Long userId = SecurityUtils.getUserId();
        String username = SecurityUtils.getUsername();
        log.info("用户退出登录请求：username={}", username);

        refreshTokenService.revokeAndBlacklistCurrent(username);

        SecurityContextHolder.clearContext();
        log.info("用户已登出: username={}", username);
    }

    /**
     * 修改密码 — 改完后强制登出
     */
    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        String username = SecurityUtils.getUsername();
        log.info("用户修改密码请求：username={}", username);

        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(request.oldPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }

        if (request.oldPassword().equals(request.newPassword())) {
            throw new BusinessException(ErrorCode.SAME_PASSWORD);
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        // 修改密码后撤销所有 token，强制重新登录
        refreshTokenService.revokeAndBlacklistCurrent(user.getUsername());
        SecurityContextHolder.clearContext();

        log.info("用户密码修改成功，已强制登出: username={}", username);
    }

    /**
     * 注销账户
     */
    @Override
    @Transactional
    public void deleteAccount() {
        Long userId = SecurityUtils.getUserId();
        String username = SecurityUtils.getUsername();
        log.info("用户注销请求：username={}", username);

        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        refreshTokenService.revokeAndBlacklistCurrent(username);

        userDataCleanupService.cleanupUserData(userId);

        userRepository.delete(user);
        SecurityContextHolder.clearContext();

        log.info("用户账户已注销: username={}", username);
    }

    @Override
    public UserEntity getCurrentUser() {
        String username = SecurityUtils.getUsername();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * 刷新 token — 无需认证，仅凭 refresh token 换发新令牌对
     */
    @Override
    public AuthResponse refreshToken(String refreshToken) {
        return refreshTokenService.refresh(refreshToken);
    }
}
