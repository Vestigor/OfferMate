package io.github.vestigor.offermate.modules.auth.service;

import io.github.vestigor.offermate.modules.auth.model.dto.AuthResponse;
import io.github.vestigor.offermate.modules.auth.model.dto.ChangePasswordRequest;
import io.github.vestigor.offermate.modules.auth.model.dto.LoginRequest;
import io.github.vestigor.offermate.modules.auth.model.dto.RegisterRequest;
import io.github.vestigor.offermate.modules.auth.model.entity.UserEntity;

public interface AuthService {
    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    void logout();

    void changePassword(ChangePasswordRequest request);

    void deleteAccount();

    UserEntity getCurrentUser();

    AuthResponse refreshToken(String refreshToken);

}