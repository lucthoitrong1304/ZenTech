package hcmute.edu.zentech.security.jwt;

import hcmute.edu.zentech.security.CustomUserDetails;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class JwtUtils {
    private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);

    @Value("${application.security.jwt.secret-key}")
    private String accessKey;

    @Value("${application.security.jwt.expiration}")
    private long accessKeyExpirationMs;

    private SecretKey getAccessTokenKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(accessKey));
    }

    public String generateJwtToken(CustomUserDetails userPrincipal) {
        List<String> roles = userPrincipal.getAuthorities().stream()
                .map(item -> item.getAuthority())
                .collect(Collectors.toList());

        return Jwts.builder()
                .subject(userPrincipal.getEmail()) // Dùng email làm định danh chính
                .claim("id", userPrincipal.getId().toString())
                .claim("roles", roles)
                .issuedAt(new Date())
                .expiration(new Date((new Date()).getTime() + accessKeyExpirationMs))
                .signWith(getAccessTokenKey())
                .compact();
    }

    public String getEmailFromJwtToken(String token) {
        return Jwts.parser()
                .verifyWith(getAccessTokenKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public boolean validateJwtToken(String authToken) {
        try {
            Jwts.parser().verifyWith(getAccessTokenKey()).build().parseSignedClaims(authToken);
            return true;
        } catch (MalformedJwtException e) {
            logger.error("Token không hợp lệ: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            logger.error("Token đã hết hạn: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            logger.error("Token không được hỗ trợ: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.error("Chuỗi JWT claims bị rỗng: {}", e.getMessage());
        } catch (SignatureException e) {
            logger.error("Chữ ký JWT không khớp: {}", e.getMessage());
        }
        return false;
    }
}