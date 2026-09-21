package com.interviewai.auth.filter;

import com.interviewai.global.error.ErrorResponse;
import com.interviewai.user.entity.User;
import com.interviewai.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class UserStatusAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;


    public UserStatusAuthenticationFilter(UserRepository userRepository, ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }


    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        if (!hasBearerToken(request)) {
            filterChain.doFilter(request, response);

            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            filterChain.doFilter(request, response);

            return;
        }

        Long userId = parseUserId(jwt.getSubject());

        if (userId == null) {
            reject(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "INVALID_ACCESS_TOKEN",
                    "유효하지 않은 엑세스 토큰입니다."
            );

            return;
        }

        User user = userRepository.findById(userId).orElse(null);

        if (user == null) {
            reject(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "INVALID_ACCESS_TOKEN",
                    "유효하지 않은 엑세스 토큰입니다."
            );

            return;
        }

        if (!user.isActive()) {
            reject(
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    "USER_SUSPENDED",
                    "정지된 사용자 계정입니다."
            );

            return;
        }

        filterChain.doFilter(request, response);
    }


    private boolean hasBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);

        return authorization != null && authorization.startsWith(BEARER_PREFIX);
    }


    private Long parseUserId(String subject) {
        try {
            long userId = Long.parseLong(subject);

            return userId > 0 ? userId : null;

        } catch (NumberFormatException exception) {
            return null;
        }
    }


    private void reject(HttpServletResponse response, int status, String code, String message) throws IOException {
        SecurityContextHolder.clearContext();

        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader("Pragma", "no-cache");

        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(code, message));
    }
}
