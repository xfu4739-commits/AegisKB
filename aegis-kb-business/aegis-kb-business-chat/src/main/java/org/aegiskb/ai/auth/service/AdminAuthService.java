package org.aegiskb.ai.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import org.aegiskb.ai.auth.dto.AdminLoginRequest;
import org.aegiskb.ai.auth.vo.AdminLoginVo;
import org.aegiskb.ai.auth.vo.AdminProfileVo;

/**
 * 后台登录认证服务。
 */
public interface AdminAuthService {

    AdminLoginVo login(AdminLoginRequest request);

    AdminProfileVo currentProfile(HttpServletRequest request);
}
