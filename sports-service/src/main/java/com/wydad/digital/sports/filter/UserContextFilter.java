package com.wydad.digital.sports.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Component
public class UserContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String email = request.getHeader("X-User-Email");
            String role = request.getHeader("X-User-Role");
            String userIdHeader = request.getHeader("X-User-Id");

            if (email != null && role != null) {
                List<GrantedAuthority> authorities = Collections.singletonList(
                        new SimpleGrantedAuthority("ROLE_" + role)
                );
                Authentication auth = new UsernamePasswordAuthenticationToken(email, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }

            // Expose l'ID/email/rôle pour les contrôleurs (ownership checks)
            if (userIdHeader != null) {
                SportsUserContext.setCurrentUserId(Long.parseLong(userIdHeader));
            }
            SportsUserContext.setCurrentUserEmail(email);
            SportsUserContext.setCurrentUserRole(role);

            chain.doFilter(request, response);
        } finally {
            SportsUserContext.clear();
        }
    }
}
