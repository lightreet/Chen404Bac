package com.chen404.aspect;

import com.chen404.annotation.RequireAdmin;
import com.chen404.domain.enums.UserRoleEnum;
import com.chen404.domain.entity.User;
import com.chen404.exception.ForbiddenException;
import com.chen404.exception.UnauthorizedException;
import com.chen404.service.support.UserAccessProfileSupport;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 对标注了 @RequireAdmin 的方法进行管理员权限校验：先校验已登录，再校验角色为管理员。
 */
@Aspect
@Component
public class RequireAdminAspect {

    @Autowired
    private UserAccessProfileSupport userAccessProfileSupport;

    @Around("@annotation(requireAdmin)")
    public Object around(ProceedingJoinPoint joinPoint, RequireAdmin requireAdmin) throws Throwable {
        HttpServletRequest request = getRequest();
        if (request == null) {
            throw new UnauthorizedException();
        }
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            throw new UnauthorizedException();
        }
        User user = userAccessProfileSupport.loadUserProfile(userId);
        if (user == null || !UserRoleEnum.ADMIN.matchesRoleCode(user.getRoleCode())) {
            throw new ForbiddenException("仅管理员可操作");
        }
        return joinPoint.proceed();
    }

    private static HttpServletRequest getRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes) {
            return ((ServletRequestAttributes) attrs).getRequest();
        }
        return null;
    }
}
