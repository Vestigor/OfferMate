package io.github.vestigor.offermate.modules.auth.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;

/**
 * 用户实体
 * User Entity for authentication and authorization
 */
@Data
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_user_username", columnList = "username", unique = true)
})
public class UserEntity implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 用户名
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    // 密码（BCrypt加密）
    @Column(nullable = false, length = 255)
    private String password;

    // 创建时间
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 最后登录时间
    private LocalDateTime lastLoginAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Spring Security UserDetails 接口实现

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.emptyList();
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    public void updateLastLogin() {
        this.lastLoginAt = LocalDateTime.now();
    }
}