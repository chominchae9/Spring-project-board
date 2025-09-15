package com.sparta.board.jwt;

import com.sparta.board.entity.enumSet.UserRoleEnum;
import com.sparta.board.security.UserDetailsServiceImpl;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import java.security.Key;
import java.util.Base64;
import java.util.Date;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtil {

    private final UserDetailsServiceImpl userDetailsService;

    public static final String AUTHORIZATION_HEADER = "Authorization"; // 헤더 이름
    public static final String AUTHORIZATION_KEY = "auth";
    private static final String BEARER_PREFIX = "Bearer "; // 토큰 앞에 붙는 접두사
    private static final long TOKEN_TIME = 60 * 60 * 1000L; // 토큰 만료 시간(1시간)

    @Value("${jwt.secret.key}")
    private String secretKey;
    private Key key;
    private final SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.HS256;

    // 초기화
    @PostConstruct
    public void init() {
        byte[] bytes = Base64.getDecoder().decode(secretKey); // secretkey를 base64 인코딩된 문자열로 저장 디코딩해 바이트 배열로 변환
        key = Keys.hmacShaKeyFor(bytes); // 전달받은 바이트 배열을 사용해서 HMAC-SHA 알고리즘용 SecretKey 객체를 생성
    }

    // header 토큰을 가져오기
    // 클라이언트가 보낸 Authorization : Bearer <JWT> 에서 JWT 본문만 추출
    public String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(7);
        }
        return null;
    }

    // 토큰 생성
    public String createToken(String username, UserRoleEnum role) {
        Date date = new Date(); // 지금 시각을 Date에 담음. 토큰 발급 시간과 만료 시간 계산에 사용

        return BEARER_PREFIX +
                Jwts.builder() // JWT 토큰 생성
                        .setSubject(username) // 토큰의 subject의 사용자 이름을 대입
                        .claim(AUTHORIZATION_KEY, role) // role : USER, ADMIN
                        .setExpiration(new Date(date.getTime() + TOKEN_TIME)) // 토큰의 만료 시간 설정
                        .setIssuedAt(date) // 토큰이 발급된 시간을 기록
                        .signWith(key, signatureAlgorithm) // 서명(signature) 생성-> 토큰 위조 여부 검증
                        .compact(); // -> 지금까지 설정한 것들을 합쳐 최종 JWT 문자열 생성
    }

    // 토큰 검증
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (SecurityException | MalformedJwtException e) {
            log.info("Invalid JWT signature, 유효하지 않는 JWT 서명 입니다.");
        } catch (ExpiredJwtException e) {
            log.info("Expired JWT token, 만료된 JWT token 입니다.");
        } catch (UnsupportedJwtException e) {
            log.info("Unsupported JWT token, 지원되지 않는 JWT 토큰 입니다.");
        } catch (IllegalArgumentException e) {
            log.info("JWT claims is empty, 잘못된 JWT 토큰 입니다.");
        }
        return false;
    }

    // 토큰에서 사용자 정보 가져오기
    public Claims getUserInfoFromToken(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
    }

    // 인증 객체 생성
    public Authentication createAuthentication(String username) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }
}

