package com.ordo.global.security;

import com.ordo.global.error.ErrorCode;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authorization: Bearer {accessToken} 을 검증해 principal 에 userId(Long)를 넣는다.
 * 실패해도 여기서 막지 않고, 사유만 요청 속성에 남겨 SecurityConfig 의 EntryPoint 가 401 JSON 으로 응답한다.
 * (@Component 로 등록하지 않는다 — 서블릿 필터로 한 번 더 등록되는 것을 막기 위해 SecurityConfig 에서 직접 생성)
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String ERROR_ATTRIBUTE = "jwtError";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            try {
                Long userId = jwtProvider.getUserId(header.substring(BEARER_PREFIX.length()));
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(userId, null, List.of()));
            } catch (ExpiredJwtException e) {
                request.setAttribute(ERROR_ATTRIBUTE, ErrorCode.EXPIRED_TOKEN);
            } catch (JwtException | IllegalArgumentException e) {
                request.setAttribute(ERROR_ATTRIBUTE, ErrorCode.UNAUTHORIZED);
            }
        }
        chain.doFilter(request, response);
    }
}
