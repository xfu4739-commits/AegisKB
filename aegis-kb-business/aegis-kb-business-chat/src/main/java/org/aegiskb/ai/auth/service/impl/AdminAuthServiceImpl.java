package org.aegiskb.ai.auth.service.impl;

import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.aegiskb.ai.auth.config.AdminAuthProperties;
import org.aegiskb.ai.auth.dto.AdminLoginRequest;
import org.aegiskb.ai.auth.service.AdminAuthService;
import org.aegiskb.ai.auth.support.AdminJwtTokenService;
import org.aegiskb.ai.auth.support.AdminRequestContext;
import org.aegiskb.ai.auth.vo.AdminLoginVo;
import org.aegiskb.ai.auth.vo.AdminProfileVo;
import org.aegiskb.exception.SuperAgentFrameException;
import org.springframework.stereotype.Service;

/**
 * 后台登录认证实现。
 */
@Service
public class AdminAuthServiceImpl implements AdminAuthService {

    private final AdminAuthProperties adminAuthProperties;

    private final AdminJwtTokenService adminJwtTokenService;

    public AdminAuthServiceImpl(AdminAuthProperties adminAuthProperties,
                                AdminJwtTokenService adminJwtTokenService) {
        this.adminAuthProperties = adminAuthProperties;
        this.adminJwtTokenService = adminJwtTokenService;
    }

    @Override
    public AdminLoginVo login(AdminLoginRequest request) {
        String username = StrUtil.trim(request.getUsername());
        String password = StrUtil.trim(request.getPassword());
        if (!StrUtil.equals(username, adminAuthProperties.getUsername())
            || !StrUtil.equals(password, adminAuthProperties.getPassword())) {
            throw new SuperAgentFrameException(401, "账号或密码不正确");
        }

        String token = adminJwtTokenService.generateToken(username);
        return new AdminLoginVo(username, token, adminAuthProperties.getTokenExpireMinutes());
    }

    @Override
    public AdminProfileVo currentProfile(HttpServletRequest request) {
        String username = AdminRequestContext.resolveUsername(request);
        return new AdminProfileVo(username);
    }
}
