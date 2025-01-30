package com.dms.document.interaction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.dms.document.interaction.client")
public class DocumentInteractionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocumentInteractionServiceApplication.class, args);
    }

}
