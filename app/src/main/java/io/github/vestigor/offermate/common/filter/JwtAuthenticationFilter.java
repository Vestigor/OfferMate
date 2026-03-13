package io.github.vestigor.offermate.common.filter;

import io.github.vestigor.offermate.common.exception.BusinessException;
import io.github.vestigor.offermate.common.exception.ErrorCode;
import io.github.vestigor.offermate.common.security.JwtService;
import io.github.vestigor.offermate.modules.auth.service.MyUserDetailsService;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT认证过滤器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final MyUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String jwt = extractJwtFromRequest(request);

            // 先检查 token 是否非空，再是验证 JWT 是否合法
            if (StringUtils.hasText(jwt) && jwtService.validateToken(jwt)) {
                String username = jwtService.getUsernameFromToken(jwt);

                // 设置 Spring Security 上下文
                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities()
                    );

                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.debug("已设置用户认证: {}", username);
                }
            }
        } catch (BusinessException e){
            writeErrorResponse(response, ErrorCode.TOKEN_BLACKLISTED);
            return;
        } catch (ExpiredJwtException e) {
            writeErrorResponse(response, ErrorCode.TOKEN_EXPIRED);
            return;
        } catch (JwtException e) {
            writeErrorResponse(response, ErrorCode.INVALID_TOKEN);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 直接向响应写入 JSON 错误体（HTTP 200 + 业务错误码）
     * 过滤器中抛出异常无法被 @RestControllerAdvice 捕获，必须在此直接写响应
     */
    private void writeErrorResponse(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json;charset=UTF-8");
        String body = String.format(
                "{\"code\":%d,\"message\":\"%s\",\"data\":null}",
                errorCode.getCode(), errorCode.getMessage()
        );
        response.getWriter().write(body);
    }

    /**
     * 从请求中提取JWT Token
     */
    public String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7); // 去掉 "Bearer " 前缀
        }

        return null;
    }
}
