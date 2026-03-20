package fun.medrec.spring.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

public class JwtUtil {
    // 密钥
    private static final String signKey = "AI drug recommendation assistant helps doctors find recommended drugs";
    // 过期时间
    private static final Long expirationTime = 3600000 * 24 * 7L;
    // 生成 Key
    private static final SecretKey signingKey = Keys.hmacShaKeyFor(signKey.getBytes(StandardCharsets.UTF_8));

    // 生成JWT令牌
    public static String generateJwt(Map<String, Object> claims) {
        return Jwts.builder()
                .claims(claims)
                .signWith(signingKey, Jwts.SIG.HS256)
                .expiration(new Date(System.currentTimeMillis() + expirationTime))
                .compact();
    }

    // 解析JWT令牌
    public static Claims parseJWT(String jwt) {
        if (!StringUtils.hasText(jwt)) {
            return null;
        }
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(jwt)
                .getPayload();
    }
}