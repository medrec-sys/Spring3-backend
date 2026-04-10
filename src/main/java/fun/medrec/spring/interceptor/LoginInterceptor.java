package fun.medrec.spring.interceptor;

import com.alibaba.fastjson.JSONObject;
import fun.medrec.spring.domain.common.Result;
import fun.medrec.spring.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;


@RequiredArgsConstructor
@Component
@Slf4j
public class LoginInterceptor implements HandlerInterceptor {
    /**
     * 请求处理前的拦截逻辑
     * @param request 当前HTTP请求
     * @param response 当前HTTP响应
     * @param handler 被调用的处理器对象
     * @return true表示继续执行请求处理链，false表示中断请求
     */
    @Override
    public boolean preHandle(HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler) throws Exception {
        log.info("LoginInterceptor 登录拦截器");
        // 获取当前请求的URI
        String requestURI = request.getRequestURI();
        log.info("当前请求URI:{}", requestURI);
        // 放行登录相关接口，避免无限重定向
        if (requestURI.contains("login")) {
            log.info("放行:{}", requestURI);
            return true;
        }
        // 从请求头中获取JWT令牌
        String jwt = request.getHeader("Authorization");
        log.info("jwt: {}", jwt);
        // 检查JWT是否存在
        if (!StringUtils.hasLength(jwt)) {
            log.info("jwt为空");
            // 返回未登录错误响应
            String notLogin = JSONObject.toJSONString(Result.error("NOT_LOGIN"));
            response.getWriter().write(notLogin);
            return false;
        }
        // 验证JWT的有效性并解析用户信息
        try {
            Claims claims = JwtUtil.parseJWT(jwt);
            assert claims != null;
            Integer id = (Integer) claims.get("id");
            String username = claims.get("username").toString();
            String account = claims.get("account").toString();
            UserContext.set(id, account, username);
        } catch (Exception e) {
            log.error("JWT解析失败", e);
            // 返回未登录错误响应
            String notLogin = JSONObject.toJSONString(Result.error("NOT_LOGIN"));
            response.getWriter().write(notLogin);
            return false;
        }
        return true;
    }
}