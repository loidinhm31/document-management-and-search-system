package com.dms.processor.config;


import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

@Configuration
@RequiredArgsConstructor
public class S3Config {
    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    private final S3Properties s3Properties;

    @Bean
    public S3Client s3Client() {
        AwsBasicCredentials awsCredentials = AwsBasicCredentials.create(
                s3Properties.getAccessKey(),
                s3Properties.getSecretKey()
        );

        if ("local".equals(activeProfile)) {
            return S3Client.builder()
                    .region(Region.of(s3Properties.getRegion()))
                    .endpointOverride(URI.create("http://localhost:4566")) // LocalStack endpoint
                    .credentialsProvider(StaticCredentialsProvider.create(awsCredentials))
                    .forcePathStyle(true)
                    .build();
        }

        return S3Client.builder()
                .region(Region.of(s3Properties.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(awsCredentials))
                .build();
    }
}
