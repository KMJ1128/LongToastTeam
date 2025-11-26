package com.longtoast.bilbil_api.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Component
public class JwtTokenProvider {

    // application.properties에서 시크릿 키와 유효 시간을 주입받아야 합니다.
    @Value("${jwt.secret}")
    private String secretKeyString;

    @Value("${jwt.expiration-time}")
    private long validityInMilliseconds;

    private Key secretKey;

    @PostConstruct
    protected void init() {
        // Base64로 인코딩된 시크릿 키를 Key 객체로 변환
        secretKey = Keys.hmacShaKeyFor(secretKeyString.getBytes());
    }

    /** 토큰 생성 메서드 */
    public String createToken(Integer userId) {
        Claims claims = Jwts.claims().setSubject(userId.toString());
        Date now = new Date();
        Date validity = new Date(now.getTime() + validityInMilliseconds);

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(validity)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /** 토큰에서 인증 정보(Authentication) 객체 가져오기 */
    public Authentication getAuthentication(String token) {
        Integer userId = getUserId(token);

        List<GrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));

        // @AuthenticationPrincipal로 주입될 객체는 Integer userId입니다.
        return new UsernamePasswordAuthenticationToken(userId, "", authorities);
    }

    /** 토큰에서 사용자 ID (Integer) 추출 */
    public Integer getUserId(String token) {
        return Integer.valueOf(Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject());
    }

    /** 토큰 유효성 검사 */
    public boolean validateToken(String token) {
        try {
            Jws<Claims> claims = Jwts.parserBuilder().setSigningKey(secretKey).build().parseClaimsJws(token);
            return !claims.getBody().getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }
}