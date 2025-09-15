package com.sparta.board.jwt;

import com.sparta.board.entity.enumSet.ErrorType;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;


    @Override
    // OncePerRequestFilter 상속 -> Request 마다 딱 한 번 실행되는 필터
    // 역할 : 클라이언트 요청이 들어올 때마다 JWT 토큰을 꺼내서 검증하고 인증 정보를 Spring Security Context 에 등록
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        // request 에 담긴 토큰을 가져온다.
        String token = jwtUtil.resolveToken(request);

        // 토큰이 null 이면 다음 필터로 넘어간다.
        if (token == null || !jwtUtil.validateToken(token)) {
            request.setAttribute("exception", ErrorType.NOT_VALID_TOKEN);
            filterChain.doFilter(request, response);
            return;
        }

        // 유효한 토큰이라면, 토큰으로부터 사용자 정보를 가져온다.
        Claims info = jwtUtil.getUserInfoFromToken(token);
        try {
            setAuthentication(info.getSubject());   // 사용자 정보로 인증 객체 만들기
        } catch (UsernameNotFoundException e) {
            request.setAttribute("exception", ErrorType.NOT_FOUND_USER);
        }
        // 다음 필터로 넘어간다.
        filterChain.doFilter(request, response);

    }

    private void setAuthentication(String username) {
        SecurityContext context = SecurityContextHolder.createEmptyContext(); // 사용자의 아이디로 Authentication 객체 생성
        Authentication authentication = jwtUtil.createAuthentication(username); // 인증 객체 만들기
        context.setAuthentication(authentication);

        SecurityContextHolder.setContext(context); // 이후 컨트롤러에서 @AuthenticationPrincipal 같은 걸로 로그인 사용자 정보 사용 가능
    }

}
