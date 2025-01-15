package com.sdms.authentication;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;


@SpringBootApplication
@EnableJpaAuditing
public class SdmsAuthenticationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SdmsAuthenticationServiceApplication.class, args);
    }

}
