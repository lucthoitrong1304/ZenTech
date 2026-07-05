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
    private static final String AI_TOOLS_AUDIENCE = "ai-tools";
    private static final String AI_TOOL_SCOPE = "AI_TOOL_CALL";
    private static final String AI_TOOL_AUTHORIZED_PARTY = "zentech-be";
    private static final long AI_TOOL_TOKEN_EXPIRATION_MS = 5 * 60 * 1000;

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

    public String generateAiToolJwtToken(CustomUserDetails userPrincipal) {
        List<String> roles = userPrincipal.getAuthorities().stream()
                .map(item -> item.getAuthority())
                .collect(Collectors.toList());

        return Jwts.builder()
                .subject(userPrincipal.getEmail())
                .claim("id", userPrincipal.getId().toString())
                .claim("roles", roles)
                .claim("scope", AI_TOOL_SCOPE)
                .claim("azp", AI_TOOL_AUTHORIZED_PARTY)
                .audience().add(AI_TOOLS_AUDIENCE).and()
                .issuedAt(new Date())
                .expiration(new Date((new Date()).getTime() + AI_TOOL_TOKEN_EXPIRATION_MS))
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
            logger.debug("Token không hợp lệ: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            logger.debug("Token đã hết hạn: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            logger.debug("Token không được hỗ trợ: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.debug("Chuỗi JWT claims bị rỗng: {}", e.getMessage());
        } catch (SignatureException e) {
            logger.debug("Chữ ký JWT không khớp: {}", e.getMessage());
        }
        return false;
    }

    public boolean validateAiToolJwtToken(String authToken) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getAccessTokenKey())
                    .build()
                    .parseSignedClaims(authToken)
                    .getPayload();

            return claims.getAudience() != null
                    && claims.getAudience().contains(AI_TOOLS_AUDIENCE)
                    && AI_TOOL_SCOPE.equals(claims.get("scope", String.class))
                    && AI_TOOL_AUTHORIZED_PARTY.equals(claims.get("azp", String.class));
        } catch (MalformedJwtException e) {
            logger.debug("AI tool token không hợp lệ: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            logger.debug("AI tool token đã hết hạn: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            logger.debug("AI tool token không được hỗ trợ: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.debug("AI tool token claims bị rỗng: {}", e.getMessage());
        } catch (SignatureException e) {
            logger.debug("AI tool token chữ ký không khớp: {}", e.getMessage());
        }
        return false;
    }
}
