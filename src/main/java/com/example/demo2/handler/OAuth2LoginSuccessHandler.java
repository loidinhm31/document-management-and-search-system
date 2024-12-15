package com.example.demo2.handler;

import com.example.demo2.entity.User;
import com.example.demo2.enums.AuthProvider;
import com.example.demo2.repository.UserRepository;
import com.example.demo2.service.CustomUserDetailsService;
import com.example.demo2.util.JwtUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        String providerId = oAuth2User.getAttribute("sub");

        User user = userRepository.findByEmail(email)
                .orElseGet(() -> createNewUser(email, name, providerId));

        String token = jwtUtil.generateToken(CustomUserDetailsService.createUserDetails(user));

        String redirectUrl = UriComponentsBuilder.fromUriString("http://localhost:3000/oauth2/redirect")
                .queryParam("token", token)
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }

    private User createNewUser(String email, String name, String providerId) {
        User user = new User();
        user.setEmail(email);
        user.setName(name);
        user.setProvider(AuthProvider.GOOGLE);
        user.setProviderId(providerId);
        Set<String> roles = new HashSet<>();
        roles.add("ROLE_USER");
        user.setRoles(roles);
        user.setEnabled(true);
        return userRepository.save(user);
    }
}