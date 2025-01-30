package com.dms.document.search.config;

import feign.RequestInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class ModelServiceFeignConfig {

    private final ModelServiceProperties modelServiceProperties;

    @Bean
    public RequestInterceptor modelServiceRequestInterceptor() {
        return requestTemplate ->
                requestTemplate.header("X-API-Key", modelServiceProperties.getApiKey());
    }
}