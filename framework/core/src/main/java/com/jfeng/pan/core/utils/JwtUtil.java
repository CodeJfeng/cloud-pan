package com.jfeng.pan.core.utils;

import cn.hutool.core.util.StrUtil;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Date;

/**
 * JWT 工具类 (基于 Hutool 和 JJWT 0.11.5 优化)
 *
 * <p>
 * 负责生成和解析 JWT Token。
 * 密钥已从字符串转换为更安全的 SecretKey 对象。
 * 异常处理更具体，提供更好的调试信息。
 * </p>
 */
public class JwtUtil {

    // --- 常量定义 ---

    /**
     * JWT 签名密钥
     * 注意：出于安全考虑，生产环境不建议将密钥硬编码或直接使用可推导的字符串。
     * 强烈建议使用强随机密钥，并通过安全的方式（如环境变量、配置中心）管理。
     * 例如: `Keys.secretKeyFor(SignatureAlgorithm.HS256)` 生成一个随机安全密钥。
     * 为保持与原逻辑兼容，这里仍基于原字符串生成。
     */
    private final static String JWT_PRIVATE_KEY_STR = "0CB16040A41140E48F2F93A7BE222C46";

    /**
     * 【修正点】将字符串密钥转换为 SecretKey 对象，JJWT 0.11.x+ 推荐的做法。
     * 确保密钥字节长度满足算法要求，HS256 需要至少 256 位 (32字节)。
     * 这里提供的字符串是32位十六进制字符，表示16字节。通常需要32字节（256位）的密钥。
     * 如果实际密钥长度不足，JJWT可能会自动延长或报错。建议使用更长的密钥。
     * 这里为了兼容，直接使用UTF-8编码。
     */
    private final static SecretKey SECRET_KEY = Keys.hmacShaKeyFor(JWT_PRIVATE_KEY_STR.getBytes(StandardCharsets.UTF_8));


    /**
     * Token 中用于续签的时间戳的 claim key
     */
    private final static String RENEWAL_TIME_CLAIM_KEY = "RENEWAL_TIME";

    // --- 生成 Token ---

    /**
     * 生成 JWT Token
     *
     * @param subject    Token 的主题 (通常是用户ID)
     * @param claimKey   自定义声明的键
     * @param claimValue 自定义声明的值
     * @param expire     Token 的过期时间（毫秒）
     * @return 生成的 JWT Token 字符串
     */
    public static String generateToken(String subject, String claimKey, Object claimValue, Long expire) {
        long nowMillis = System.currentTimeMillis();
        Date now = new Date(nowMillis);
        Date expirationDate = new Date(nowMillis + expire);

        // 续签时间点：在 Token 过期时间的一半时触发续签
        Date renewalTime = new Date(nowMillis + expire / 2L); // 直接使用 2L 即可

        String token = Jwts.builder()
//                .header()
                .setSubject(subject) // 设置主题
                .claim(claimKey, claimValue) // 添加自定义声明
                .claim(RENEWAL_TIME_CLAIM_KEY, renewalTime) // 添加续签时间戳
                .setIssuedAt(now) // 设置签发时间
                .setExpiration(expirationDate) // 设置过期时间
                .signWith(SECRET_KEY, SignatureAlgorithm.HS256) // 【修正点】使用 SecretKey 和算法签名
                .compact(); // 压缩生成 Token 字符串
        return token;
    }

    // --- 解析 Token ---

    /**
     * 解析 JWT Token，获取指定声明的值
     *
     * @param token    需要解析的 JWT Token 字符串
     * @param claimKey 需要获取的声明的键
     * @return 指定声明的值，如果 Token 无效或解析失败则返回 null
     */
    public static Object analyzeToken(String token, String claimKey) {
        // 【修正点】使用 StrUtil 进行空字符串检查
        if (StrUtil.isBlank(token)) {
            return null;
        }
        try {
            JwtParser jwtParser = Jwts.parser()
                    // 设置签名的秘钥
                    .verifyWith(SECRET_KEY)
                    .build();
            Jws<Claims> jws = jwtParser.parseSignedClaims(token);
            Claims claims = jws.getPayload();
            return  claims.get(claimKey);

            // 【修正点】Jwts.parser() 返回 JwtParserBuilder，需要调用 .build()
//            return Jwts.parser()
////                    .requireIssuer(claimKey)
//                    .verifyWith(SECRET_KEY)
//                    .build()
//                    .parseUnsecuredClaims(token)
//                    .getPayload()
//                    .get(claimKey);
        } catch (ExpiredJwtException e) {
            // Token 过期异常
            System.err.println("JWT Token expired: " + e.getMessage());
            return null;
        } catch (SignatureException e) {
            // 签名验证失败异常 (密钥不匹配或Token被篡改)
            System.err.println("JWT Signature validation failed: " + e.getMessage());
            return null;
        } catch (MalformedJwtException e) {
            // JWT 格式不正确异常
            System.err.println("JWT Malformed: " + e.getMessage());
            return null;
        } catch (UnsupportedJwtException e) {
            // 不支持的 JWT 类型异常
            System.err.println("JWT Unsupported: " + e.getMessage());
            return null;
        } catch (IllegalArgumentException e) {
            // 非法参数异常 (如 Token 为 null 或空字符串)
            System.err.println("JWT Illegal argument: " + e.getMessage());
            return null;
        } catch (Exception e) {
            // 其他未知异常
            System.err.println("Error analyzing JWT Token: " + e.getMessage());
            e.printStackTrace(); // 兜底打印堆栈，便于调试未预料的异常
            return null;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        // 测试生成Token
        String token = generateToken("user123", "userId", 1001L, 30000L); // 30秒过期
        System.out.println("Generated Token: " + token);

        // 测试解析Token
        Object userId = (Object) analyzeToken(token, "userId");
        Object renewalTime = (Object) analyzeToken(token, RENEWAL_TIME_CLAIM_KEY);
        Date expirationTime = (Date) Jwts.parser().setSigningKey(SECRET_KEY).build().parseClaimsJws(token).getBody().getExpiration(); // 直接获取过期时间
        System.out.println("Parsed User ID: " + userId);
        System.out.println("Renewal Time: " + renewalTime);
        System.out.println("Expiration Time: " + expirationTime);

        // 测试过期Token (等待30秒以上)
        System.out.println("Waiting 31 seconds to test expired token...");
        Thread.sleep(31000);
        Object expiredUserId = analyzeToken(token, "userId");
        System.out.println("Parsed Expired User ID: " + expiredUserId); // 应该为 null

        // 测试无效Token
        System.out.println("Testing invalid token...");
        Object invalidUserId = analyzeToken("invalid.token.string", "userId");
        System.out.println("Parsed Invalid User ID: " + invalidUserId); // 应该为 null
    }
}