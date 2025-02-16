package com.dms.document.interaction.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class FeignConfig {

    @Value("${app.auth-service.api-key}")
    private String serviceApiKey;

    @Bean
    public RequestInterceptor bearerTokenRequestInterceptor() {
        return requestTemplate -> {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                // Pass user's JWT token for user context
                BearerTokenResolver resolver = new DefaultBearerTokenResolver();
                String token = resolver.resolve(attributes.getRequest());
                if (token != null) {
                    requestTemplate.header("Authorization", "Bearer " + token);
                }

                // Add service API key for service authentication
                requestTemplate.header("X-Service-API-Key", serviceApiKey);
            }
        };
    }
}