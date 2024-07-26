package com.example.core.jwt;

import com.example.core.service.UserDetailsImpl;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import java.security.Key;
import java.util.Date;
import java.util.List;

@Component
public class JwtUtils {
  private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);

  @Value("${bezkoder.app.jwtSecret}")
  private String jwtSecret;

  @Value("${bezkoder.app.jwtCookieName}")
  private String jwtCookieName;

  @Value("${bezkoder.app.jwtExpirationMs}")
  private int jwtExpirationMs;

  private Key key;

  @PostConstruct
  public void init() {
    if (jwtSecret.length() < 64) {
      throw new IllegalArgumentException("JWT secret key must be at least 64 characters long for HS512");
    }
    this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
  }

  public String getJwtFromCookies(HttpServletRequest request) {
    Cookie cookie = WebUtils.getCookie(request, jwtCookieName);
    if (cookie != null) {
      return cookie.getValue();
    }
    return null;
  }

  public ResponseCookie generateJwtCookie(UserDetailsImpl userPrincipal) {
    String jwt = generateTokenFromUsername(userPrincipal);
    return ResponseCookie.from(jwtCookieName, jwt)
            .path("/api")
            .maxAge(24 * 60 * 60)
            .httpOnly(true)
            .build();
  }

  public ResponseCookie getCleanJwtCookie() {
    return ResponseCookie.from(jwtCookieName, null)
            .path("/api")
            .build();
  }

  public String getUserNameFromJwtToken(String token) {
    return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody().getSubject();
  }

  public boolean validateJwtToken(String authToken) {
    try {
      Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(authToken);
      return true;
    } catch (SignatureException e) {
      logger.error("Invalid JWT signature: {}", e.getMessage());
    } catch (MalformedJwtException e) {
      logger.error("Invalid JWT token: {}", e.getMessage());
    } catch (ExpiredJwtException e) {
      logger.error("JWT token is expired: {}", e.getMessage());
    } catch (UnsupportedJwtException e) {
      logger.error("JWT token is unsupported: {}", e.getMessage());
    } catch (IllegalArgumentException e) {
      logger.error("JWT claims string is empty: {}", e.getMessage());
    }

    return false;
  }

  public String generateTokenFromUsername(UserDetailsImpl userPrincipal) {
    return Jwts.builder()
            .setSubject(userPrincipal.getUsername())
            .claim("roles", userPrincipal.getAuthorities().stream()
                    .map(authority -> authority.getAuthority()).toList())
            .setIssuedAt(new Date())
            .setExpiration(new Date((new Date()).getTime() + jwtExpirationMs))
            .signWith(key, SignatureAlgorithm.HS512)
            .compact();
  }

  public Claims getClaimsFromJwtToken(String token) {
    return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
  }

  public List<String> getRolesFromJwtToken(String token) {
    Claims claims = getClaimsFromJwtToken(token);
    return claims.get("roles", List.class);
  }
}
