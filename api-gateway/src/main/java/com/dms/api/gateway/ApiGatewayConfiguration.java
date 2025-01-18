package com.dms.api.gateway;


import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApiGatewayConfiguration {
    @Bean
    public RouteLocator gatewayRouter(RouteLocatorBuilder builder) {
        return builder.routes()
                .route(r-> r.path("/auth/**")
                        .filters(f -> f.rewritePath(
                                "/auth/(?<segment>.*)",
                                "/${segment}"))
                        .uri("lb://auth-service"))
                .route(r-> r.path("/document/**")
                        .filters(f -> f.rewritePath(
                                "/document/(?<segment>.*)",
                                "/${segment}"))
                        .uri("lb://document-service"))
                .build();
    }
}