package fun.medrec.spring.utils;

import fun.medrec.spring.exception.BusinessException;
import org.mindrot.jbcrypt.BCrypt;

/**
 * 密码加密工具类
 * 使用BCrypt算法实现不可逆加密
 */
public class BCryptUtil {
    /**
     * 加密用户密码
     * @param rawPassword 原始密码
     * @return 加密后的密码
     */
    public static String encode(String rawPassword) {
        if (rawPassword == null || rawPassword.trim().isEmpty()) {
            throw new BusinessException("密码不能为空");
        }
        // 生成盐并哈希密码
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt());
    }

    /**
     * 验证密码是否匹配
     * @param rawPassword 原始密码
     * @param encodedPassword 加密后的密码
     * @return 是否匹配
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || rawPassword.trim().isEmpty()) {
            return false;
        }
        if (encodedPassword == null || encodedPassword.trim().isEmpty()) {
            return false;
        }
        return BCrypt.checkpw(rawPassword, encodedPassword);
    }
}