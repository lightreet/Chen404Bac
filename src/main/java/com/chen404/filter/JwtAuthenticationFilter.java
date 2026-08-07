package com.chen404.filter;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.chen404.config.PublicApiRequestMatcher;
import com.chen404.domain.ApiErrorCode;
import com.chen404.domain.Result;
import com.chen404.domain.entity.User;
import com.chen404.domain.enums.UserRoleEnum;
import com.chen404.security.AuthenticatedUser;
import com.chen404.service.AuthSessionService;
import com.chen404.service.UserService;
import com.chen404.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 仅接受当前会话版本的访问令牌，并把有效用户身份写入 Spring Security 上下文。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String REQUEST_USER_ID_ATTR = "userId";
    private static final int USER_ENABLED_STATUS = 1;

    private final JwtUtil jwtUtil;
    private final AuthSessionService authSessionService;
    private final UserService userService;
    private final PublicApiRequestMatcher publicApiRequestMatcher;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(
            JwtUtil jwtUtil,
            AuthSessionService authSessionService,
            UserService userService,
            PublicApiRequestMatcher publicApiRequestMatcher,
            ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.authSessionService = authSessionService;
        this.userService = userService;
        this.publicApiRequestMatcher = publicApiRequestMatcher;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean publicRequest = publicApiRequestMatcher.matches(request);
        String token = extractBearerToken(request);
        if (token == null) {
            if (publicRequest) {
                filterChain.doFilter(request, response);
                return;
            }
            writeUnauthorized(response, "未登录或登录已过期");
            return;
        }

        try {
            var decoded = jwtUtil.verifyAccessToken(token);
            Long userId = jwtUtil.getUserId(decoded);
            if (!authSessionService.isCurrent(userId, decoded)) {
                writeUnauthorized(response, "登录状态已失效，请重新登录");
                return;
            }
            User user = userService.getCurrentUser(userId);
            if (user == null || user.getStatus() == null || user.getStatus() != USER_ENABLED_STATUS) {
                writeUnauthorized(response, "用户不存在或已被禁用");
                return;
            }

            request.setAttribute(REQUEST_USER_ID_ATTR, userId);
            AuthenticatedUser principal = new AuthenticatedUser(userId, user.getUsername(), user.getRoleCode());
            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                    principal,
                    token,
                    buildAuthorities(user)
            );
            authenticationToken.setDetails(decoded);
            SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            filterChain.doFilter(request, response);
        } catch (JWTVerificationException ex) {
            SecurityContextHolder.clearContext();
            if (publicRequest) {
                filterChain.doFilter(request, response);
                return;
            }
            writeUnauthorized(response, "Token 无效或已过期");
        }
    }

    private String extractBearerToken(HttpServletRequest request) {
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return authHeader.substring(BEARER_PREFIX.length());
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(ApiErrorCode.UNAUTHORIZED, message)));
    }

    private String toRoleAuthority(String roleCode) {
        UserRoleEnum role = UserRoleEnum.fromRoleCode(roleCode);
        return "ROLE_" + role.name();
    }

    private List<GrantedAuthority> buildAuthorities(User user) {
        List<GrantedAuthority> authorities = new ArrayList<>(
                AuthorityUtils.createAuthorityList(toRoleAuthority(user.getRoleCode()))
        );
        if (user.getCapabilities() == null) {
            return authorities;
        }
        user.getCapabilities().stream()
                .map(this::toCapabilityAuthority)
                .map(SimpleGrantedAuthority::new)
                .forEach(authorities::add);
        return authorities;
    }

    private String toCapabilityAuthority(String capabilityCode) {
        return "CAP_" + capabilityCode
                .replace(':', '_')
                .replace('-', '_')
                .toUpperCase(Locale.ROOT);
    }
}
