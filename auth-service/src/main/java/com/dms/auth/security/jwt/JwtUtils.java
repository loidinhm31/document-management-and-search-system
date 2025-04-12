package com.dms.auth.security.jwt;

import com.dms.auth.entity.AuthToken;
import com.dms.auth.enums.TokenType;
import com.dms.auth.service.TokenService;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.dms.auth.security.service.CustomUserDetails;
import io.jsonwebtoken.*;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class JwtUtils {
    @Value("${spring.app.accessTokenExpirationMs}")
    private int accessTokenExpirationMs;

    private final TokenService tokenService;

    private RSAPrivateKey privateKey;
    @Getter
    private RSAPublicKey publicKey;
    private String keyId;

    public JwtUtils(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @PostConstruct
    public void init() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();

            this.privateKey = (RSAPrivateKey) keyPair.getPrivate();
            this.publicKey = (RSAPublicKey) keyPair.getPublic();
            this.keyId = UUID.randomUUID().toString();
        } catch (Exception e) {
            log.error("Failed to initialize JWT keys", e);
            throw new RuntimeException("Failed to initialize JWT keys", e);
        }
    }

    public String getJwtFromHeader(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    public String generateTokenFromUsername(CustomUserDetails userDetails) {
        Map<String, Object> headers = new HashMap<>();
        headers.put("kid", keyId);
        headers.put("typ", "JWT");

        return Jwts.builder()
                .header().add(headers)
                .and()
                .issuer("dms-auth-service")
                .subject(userDetails.getUsername())
                .claim("is2faEnabled", userDetails.is2faEnabled())
                .claim("accountNonLocked", userDetails.isAccountNonLocked())
                .claim("enabled", userDetails.isEnabled())
                .claim("roles", userDetails.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toList()))
                .issuedAt(new Date())
                .expiration(new Date((new Date()).getTime() + accessTokenExpirationMs))
                .signWith(privateKey, SignatureAlgorithm.RS256)
                .compact();
    }

    public String getUserNameFromJwtToken(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public Map<String, Object> getJwks() {
        RSAKey.Builder builder = new RSAKey.Builder(publicKey)
                .keyID(keyId)
                .algorithm(JWSAlgorithm.RS256)
                .keyUse(KeyUse.SIGNATURE);

        JWKSet jwkSet = new JWKSet(builder.build());
        return jwkSet.toJSONObject();
    }

    public boolean validateJwtToken(String authToken) {
        try {
            // Perform existing signature validation
            Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(authToken);

            // Check if token has been revoked by its hash
            String tokenHash = DigestUtils.sha256Hex(authToken);
            Optional<AuthToken> storedToken = tokenService.findByTokenAndType(tokenHash, TokenType.ACCESS);
            if (storedToken.isPresent() && storedToken.get().isRevoked()) {
                log.info("JWT token is revoked: {}", authToken);
                return false;
            }

            return true;
        } catch (Exception e) {
            log.info("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }
}