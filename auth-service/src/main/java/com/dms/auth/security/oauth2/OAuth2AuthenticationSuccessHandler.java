package com.dms.auth.security.oauth2;

import com.dms.auth.entity.RefreshToken;
import com.dms.auth.entity.Role;
import com.dms.auth.entity.User;
import com.dms.auth.enums.AppRole;
import com.dms.auth.repository.RoleRepository;
import com.dms.auth.repository.UserRepository;
import com.dms.auth.security.jwt.JwtUtils;
import com.dms.auth.security.services.CustomUserDetails;
import com.dms.auth.service.impl.RefreshTokenService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenService refreshTokenService;
    private final JwtUtils jwtUtils;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    private static final String GOOGLE_PROVIDER = "google";
    private static final String GOOGLE_ID_ATTRIBUTE = "sub";
    private static final String EMAIL_ATTRIBUTE = "email";

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws ServletException, IOException {
        OAuth2AuthenticationToken oAuth2Token = (OAuth2AuthenticationToken) authentication;

        if (!GOOGLE_PROVIDER.equals(oAuth2Token.getAuthorizedClientRegistrationId())) {
            log.warn("Unsupported OAuth2 provider: {}", oAuth2Token.getAuthorizedClientRegistrationId());
            return;
        }

        processGoogleAuthentication(oAuth2Token);
        handleRedirect(request, response, authentication);
    }

    private void processGoogleAuthentication(OAuth2AuthenticationToken oAuth2Token) {
        DefaultOAuth2User principal = (DefaultOAuth2User) oAuth2Token.getPrincipal();
        Map<String, Object> attributes = principal.getAttributes();
        String email = attributes.getOrDefault(EMAIL_ATTRIBUTE, "").toString();

        userRepository.findByEmail(email)
                .ifPresentOrElse(
                        user -> handleExistingUser(user, attributes, oAuth2Token),
                        () -> handleNewUser(email, attributes, oAuth2Token)
                );
    }

    private void handleExistingUser(User user, Map<String, Object> attributes, OAuth2AuthenticationToken oAuth2Token) {
        updateSecurityContext(user, attributes, oAuth2Token);
    }

    private void handleNewUser(String email, Map<String, Object> attributes, OAuth2AuthenticationToken oAuth2Token) {
        Role userRole = roleRepository.findByRoleName(AppRole.ROLE_USER)
                .orElseThrow(() -> new RuntimeException("Default role not found"));

        String uniqueUsername = generateUniqueUsername(email);
        User newUser = createNewUser(email, uniqueUsername, userRole, oAuth2Token.getAuthorizedClientRegistrationId());
        userRepository.save(newUser);

        updateSecurityContext(newUser, attributes, oAuth2Token);
    }

    private User createNewUser(String email, String username, Role role, String signUpMethod) {
        User newUser = new User();
        newUser.setRole(role);
        newUser.setEmail(email);
        newUser.setUsername(username);
        newUser.setSignUpMethod(signUpMethod);
        newUser.setCreatedBy(username);
        newUser.setUpdatedBy(username);
        newUser.setEnabled(true);
        newUser.setAccountNonExpired(true);
        newUser.setAccountNonLocked(true);
        newUser.setCredentialsNonExpired(true);
        return newUser;
    }

    private void updateSecurityContext(User user, Map<String, Object> attributes, OAuth2AuthenticationToken oAuth2Token) {
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority(user.getRole().getRoleName().name());
        DefaultOAuth2User oauthUser = new DefaultOAuth2User(
                Collections.singletonList(authority),
                attributes,
                GOOGLE_ID_ATTRIBUTE
        );

        Authentication securityAuth = new OAuth2AuthenticationToken(
                oauthUser,
                Collections.singletonList(authority),
                oAuth2Token.getAuthorizedClientRegistrationId()
        );

        SecurityContextHolder.getContext().setAuthentication(securityAuth);
    }

    private String generateUniqueUsername(String email) {
        String baseUsername = sanitizeUsername(email.split("@")[0]);
        if (!userRepository.existsByUsername(baseUsername)) {
            return baseUsername;
        }

        int counter = 1;
        String proposedUsername;
        do {
            proposedUsername = baseUsername + counter;
            counter++;
        } while (userRepository.existsByUsername(proposedUsername));

        return proposedUsername;
    }

    private String sanitizeUsername(String username) {
        // Remove special characters and limit length
        return username.replaceAll("[^a-zA-Z0-9.]", "")
                .toLowerCase()
                .substring(0, Math.min(username.length(), 50));
    }

    private void handleRedirect(HttpServletRequest request, HttpServletResponse response,
                                Authentication authentication) throws ServletException, IOException {
        this.setAlwaysUseDefaultTargetUrl(true);

        DefaultOAuth2User oauth2User = (DefaultOAuth2User) authentication.getPrincipal();
        String email = oauth2User.getAttribute(EMAIL_ATTRIBUTE);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Set<SimpleGrantedAuthority> authorities = getUpdatedAuthorities(oauth2User, user);
        String jwtToken = generateJwtToken(user, email, authorities);

        // Generate refresh token
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user, request);

        String targetUrl = buildRedirectUrl(jwtToken, refreshToken.getToken());

        this.setDefaultTargetUrl(targetUrl);
        super.onAuthenticationSuccess(request, response, authentication);
    }

    private Set<SimpleGrantedAuthority> getUpdatedAuthorities(DefaultOAuth2User oauth2User, User user) {
        Set<SimpleGrantedAuthority> authorities = oauth2User.getAuthorities().stream()
                .map(authority -> new SimpleGrantedAuthority(authority.getAuthority()))
                .collect(Collectors.toSet());
        authorities.add(new SimpleGrantedAuthority(user.getRole().getRoleName().name()));
        return authorities;
    }

    private String generateJwtToken(User user, String email, Set<SimpleGrantedAuthority> authorities) {
        CustomUserDetails userDetails = new CustomUserDetails(
                null,
                user.getUsername(),
                email,
                null,
                user.isTwoFactorEnabled(),
                authorities
        );
        return jwtUtils.generateTokenFromUsername(userDetails);
    }

    private String buildRedirectUrl(String accessToken, String refreshToken) {
        return UriComponentsBuilder.fromUriString(frontendUrl + "/oauth2/redirect")
                .queryParam("token", accessToken)
                .queryParam("refreshToken", refreshToken)
                .build().toUriString();
    }
}