package com.dms.document.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.dms.document.search.client")
public class DocumentSearchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocumentSearchServiceApplication.class, args);
    }

}
