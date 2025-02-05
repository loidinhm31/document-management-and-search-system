package com.dms.auth.security.jwt;

import com.dms.auth.security.services.UserDetailsServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Slf4j
@Component
public class AuthTokenFilter extends OncePerRequestFilter {
    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    @Value("${app.auth-service.service-api-keys.document-interaction-service}")
    private String documentInteractionServiceKey;

    @Value("${app.auth-service.service-api-keys.document-search-service}")
    private String documentSearchServiceKey;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        log.info("AuthTokenFilter called for URI: {}", request.getRequestURI());
        try {
            String jwt = parseJwt(request);
            String apiKey = request.getHeader("X-Service-API-Key");

            if (jwt != null && jwtUtils.validateJwtToken(jwt)) {
                String username = jwtUtils.getUserNameFromJwtToken(jwt);
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                // Get existing authorities
                Collection<? extends GrantedAuthority> existingAuthorities = userDetails.getAuthorities();
                log.info("Roles from JWT: {}", userDetails.getAuthorities());

                List<GrantedAuthority> updatedAuthorities = new ArrayList<>(existingAuthorities);

                // Add ROLE_SERVICE if valid API key is present
                if (isValidApiKey(apiKey)) {
                    updatedAuthorities.add(new SimpleGrantedAuthority("ROLE_SERVICE"));
                    log.info("Added ROLE_SERVICE for service call from {}", username);
                }

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails,
                                null,
                                updatedAuthorities);

                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                log.info("User roles: {}", updatedAuthorities);

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            log.error("Cannot set user authentication", e);
        }

        filterChain.doFilter(request, response);
    }

    private String parseJwt(HttpServletRequest request) {
        String jwt = jwtUtils.getJwtFromHeader(request);
        log.debug("AuthTokenFilter parsed JWT: {}", jwt);
        return jwt;
    }

    private boolean isValidApiKey(String apiKey) {
        // Compare with configured service API keys
        return apiKey != null && (
                apiKey.equals(documentInteractionServiceKey) ||
                        apiKey.equals(documentSearchServiceKey)
        );
    }
}